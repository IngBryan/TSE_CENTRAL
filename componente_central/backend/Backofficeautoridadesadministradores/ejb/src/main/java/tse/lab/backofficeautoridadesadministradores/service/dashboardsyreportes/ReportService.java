package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.ReportRequest.KpiRequest;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.ReportRequest.Range;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.ReportResult.Row;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.Facet;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.FacetValue;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.Facets;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.TimeFacet;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.Kpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.KpiValue;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.Kpis;

/**
 * Calculates a report: each KPI grouped by the facet, over the period and, when a measure needs it, over the
 * comparison. Grouped by time, row i of the period is compared with group i of the comparison (November 2026 with
 * November 2025); grouped by anything else, each group with the same group.
 */
@Stateless
public class ReportService {

    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    @PersistenceContext(unitName = "backofficeautoridadesadministradoresPersistenceUnit")
    private EntityManager em;

    public ReportService() {
    }

    ReportService(EntityManager em) {
        this.em = em;
    }

    private record Column(Kpi kpi, List<Measure> measures, List<KpiValue> current, List<KpiValue> previous) {
    }

    // Every value of a catalog facet, with no filter: what a filter lists. Time has none: its groups are the days,
    // weeks, months or years of the period
    public List<FacetValue> values(String facetId) {
        Facet facet = Facets.byId(facetId);
        if (facet == null) {
            throw new FacetNotFoundException(facetId);
        }
        if (facet instanceof TimeFacet) {
            throw new InvalidReportException("The facet " + facetId + " has no fixed values");
        }
        return facet.values(em);
    }

    public List<KpiDefinition> kpis() {
        return Kpis.ALL.stream().map(kpi -> new KpiDefinition(kpi.id(), kpi.name())).toList();
    }

    // The facets a report of all the KPIs at once can group by, in the order Facets lists them; time only groups,
    // the others also filter
    public List<FacetDefinition> facets(List<String> kpiIds) {
        if (kpiIds == null || kpiIds.isEmpty()) {
            throw new InvalidReportException("Ask for the facets of at least one KPI");
        }
        List<Kpi> kpis = new ArrayList<>();
        for (String id : kpiIds) {
            Kpi kpi = Kpis.byId(id);
            if (kpi == null) {
                throw new KpiNotFoundException(id);
            }
            kpis.add(kpi);
        }
        return Facets.ALL.stream()
                .filter(facet -> kpis.stream().allMatch(kpi -> kpi.supports(facet)))
                .map(facet -> new FacetDefinition(facet.id(), facet.name(),
                        facet instanceof TimeFacet ? "time" : "catalog"))
                .toList();
    }

    public ReportResult load(ReportRequest request) {
        if (request == null || request.kpis() == null || request.kpis().isEmpty()) {
            throw new InvalidReportException("A report needs at least one KPI");
        }
        Map<Kpi, List<Measure>> kpis = kpis(request.kpis());
        Period period = period(request.period(), "period");
        boolean compares = kpis.values().stream().flatMap(List::stream).anyMatch(Measure::needsComparison);
        Period comparison = compares ? period(request.comparison(), "comparison") : null;
        Facet grouping = facet(request.groupBy(), "group by");
        Map<Facet, Selection> filters = filters(request.filters());

        List<Column> columns = new ArrayList<>();
        Map<String, Map<String, Double>> total = new LinkedHashMap<>();
        try {
            kpis.forEach((kpi, measures) -> {
                boolean previous = measures.stream().anyMatch(Measure::needsComparison);
                columns.add(new Column(kpi, measures, kpi.calculate(em, period, grouping, filters),
                        previous ? kpi.calculate(em, comparison, grouping, filters) : null));
                total.put(kpi.id(), measures(measures, kpi.total(em, period, filters),
                        previous ? kpi.total(em, comparison, filters) : null));
            });
        } catch (IllegalArgumentException e) {
            throw new InvalidReportException(e.getMessage());
        }

        List<Row> rows = grouping instanceof TimeFacet time
                ? timeRows(time, period, comparison, columns)
                : rows(columns);
        return new ReportResult(grouping.id(), rows, total);
    }

    // Every day, week, month or year of the period, even without rows, next to the same one of the comparison
    private List<Row> timeRows(TimeFacet time, Period period, Period comparison, List<Column> columns) {
        List<FacetValue> groups = time.buckets(period).stream().map(TimeFacet.Bucket::value).toList();
        List<FacetValue> previousGroups = comparison == null
                ? List.of()
                : time.buckets(comparison).stream().map(TimeFacet.Bucket::value).toList();

        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            FacetValue previousGroup = i < previousGroups.size() ? previousGroups.get(i) : null;
            rows.add(row(groups.get(i), previousGroup, previousGroup != null, columns));
        }
        return rows;
    }

    // The groups in the order of the first KPI, the biggest first; the ones only another KPI or the comparison has
    // after them, and the one without value last
    private List<Row> rows(List<Column> columns) {
        Set<FacetValue> groups = new LinkedHashSet<>();
        columns.forEach(column -> column.current().forEach(value -> groups.add(value.group())));
        columns.stream().filter(column -> column.previous() != null)
                .forEach(column -> column.previous().forEach(value -> groups.add(value.group())));

        List<Row> rows = new ArrayList<>();
        groups.stream().filter(Objects::nonNull).forEach(group -> rows.add(row(group, group, true, columns)));
        if (groups.contains(null)) {
            rows.add(row(null, null, true, columns));
        }
        return rows;
    }

    // hasPrevious is false for a group of the period the comparison has no counterpart for
    private Row row(FacetValue group, FacetValue previousGroup, boolean hasPrevious, List<Column> columns) {
        Map<String, Map<String, Double>> values = new LinkedHashMap<>();
        for (Column column : columns) {
            Double current = valueOf(column.kpi(), column.current(), group);
            Double previous = column.previous() == null || !hasPrevious
                    ? null
                    : valueOf(column.kpi(), column.previous(), previousGroup);
            values.put(column.kpi().id(), measures(column.measures(), current, previous));
        }
        return new Row(group, values);
    }

    private static Double valueOf(Kpi kpi, List<KpiValue> values, FacetValue group) {
        return values.stream()
                .filter(value -> Objects.equals(value.group(), group))
                .findFirst()
                .map(value -> (Double) value.value())
                .orElse(kpi.valueWithoutRows());
    }

    // Not Map.of: a measure without value stays, as null
    private static Map<String, Double> measures(List<Measure> measures, Double current, Double previous) {
        Map<String, Double> values = new LinkedHashMap<>();
        measures.forEach(measure -> values.put(measure.id(), measure.of(current, previous)));
        return values;
    }

    private static Map<Kpi, List<Measure>> kpis(List<KpiRequest> requests) {
        Map<Kpi, List<Measure>> kpis = new LinkedHashMap<>();
        for (KpiRequest request : requests) {
            Kpi kpi = request == null ? null : Kpis.byId(request.id());
            if (kpi == null) {
                throw new InvalidReportException("Unknown KPI: " + (request == null ? null : request.id()));
            }
            if (request.measures() == null || request.measures().isEmpty()) {
                throw new InvalidReportException("The KPI " + kpi.id() + " needs at least one measure");
            }
            Set<Measure> measures = new LinkedHashSet<>();
            for (String id : request.measures()) {
                Measure measure = Measure.byId(id);
                if (measure == null) {
                    throw new InvalidReportException("Unknown measure: " + id);
                }
                measures.add(measure);
            }
            if (kpis.put(kpi, List.copyOf(measures)) != null) {
                throw new InvalidReportException("The KPI " + kpi.id() + " is asked for twice");
            }
        }
        return kpis;
    }

    // Whole days only: from the start of the first one to the end of the last one
    private static Period period(Range range, String name) {
        if (range == null || range.from() == null || range.to() == null) {
            throw new InvalidReportException("The report needs a " + name + " with from and to");
        }
        LocalDateTime from;
        LocalDateTime to;
        try {
            from = LocalDateTime.parse(range.from());
            to = LocalDateTime.parse(range.to());
        } catch (DateTimeParseException e) {
            throw new InvalidReportException("The " + name + " dates must look like 2026-11-07T23:59:59.999");
        }
        if (!from.toLocalTime().equals(LocalTime.MIDNIGHT)
                || !to.toLocalTime().truncatedTo(ChronoUnit.SECONDS).equals(END_OF_DAY)) {
            throw new InvalidReportException("The " + name + " must go from the start of a day to the end of one");
        }
        if (from.isAfter(to)) {
            throw new InvalidReportException("The " + name + " ends before it starts");
        }
        return new Period(from.toLocalDate(), to.toLocalDate());
    }

    private static Facet facet(String id, String use) {
        Facet facet = Facets.byId(id);
        if (facet == null) {
            throw new InvalidReportException("Unknown facet to " + use + ": " + id);
        }
        return facet;
    }

    // The period is the only filter of time
    private static Map<Facet, Selection> filters(Map<String, Selection> requested) {
        Map<Facet, Selection> filters = new HashMap<>();
        if (requested == null) {
            return filters;
        }
        requested.forEach((id, selection) -> {
            Facet facet = facet(id, "filter by");
            if (facet instanceof TimeFacet) {
                throw new InvalidReportException("Time is filtered by the period, not by " + id);
            }
            filters.put(facet, selection == null ? Selection.ALL : selection);
        });
        return filters;
    }
}

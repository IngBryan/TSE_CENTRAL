package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Period;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Selection;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.TableAlias;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.Facet;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.Facet.Link;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.FacetValue;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.Facets;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.TimeFacet;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.Kpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.KpiValue;

/**
 * A KPI with a query of its own: the tables it uses and a base query over them (what it measures and the rows it
 * always keeps). calculate() narrows it to a period and the filters and groups it by a facet; each facet knows how
 * to get its value from the tables, so the KPI never deals with dates, departments or cargo types.
 */
public abstract class BaseQueryKpi extends Kpi {

    protected BaseQueryKpi(String id, String name) {
        super(id, name);
    }

    // The tables of the query: first the one it counts, then the ones it joins, in the order the facets look at
    protected abstract List<TableAlias> tables();

    // The aggregate it reports, over the aliases of its tables: "COUNT(g)"
    protected abstract String measure();

    // Rows it always keeps, or null: "g.estado = tse.lab.model.EstadoGuia.FINALIZADA"
    protected abstract String condition();

    @Override
    public boolean supports(Facet facet) {
        return facet.resolve(tables()) != null;
    }

    @Override
    protected List<KpiValue> compute(EntityManager em, Period period, Facet grouping, Map<Facet, Selection> filters) {
        Map<String, Object> parameters = new HashMap<>();
        // The joins the facets in use need, each once even when several facets share it (a case's trip)
        Set<String> joins = new LinkedHashSet<>();

        Link groupLink = grouping == null ? null : resolve(grouping, "grouped");
        List<String> groups = groupLink == null ? List.of() : grouping.groupBy(groupLink);
        if (groupLink != null) {
            joins.addAll(groupLink.joins());
        }

        List<String> conditions = new ArrayList<>();
        if (condition() != null) {
            // In parentheses: an OR in it must not swallow the period and the filters joined with AND
            conditions.add("(" + condition() + ")");
        }
        // A KPI whose main table has no day (companies, vehicles) counts what there is at the end of the period
        Link day = Facets.DAY.resolve(tables());
        if (day != null) {
            conditions.add(day.id() + " BETWEEN :from AND :to");
        }
        filters.forEach((facet, selection) -> {
            Link link = resolve(facet, "filtered");
            joins.addAll(link.joins());
            conditions.addAll(facet.filter(link, selection, parameters));
        });

        StringBuilder jpql = new StringBuilder("SELECT ");
        groups.forEach(group -> jpql.append(group).append(", "));
        jpql.append(measure()).append(" AS kpiValue FROM ").append(from());
        joins.forEach(join -> jpql.append(" ").append(join));
        if (!conditions.isEmpty()) {
            jpql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        if (!groups.isEmpty()) {
            jpql.append(" GROUP BY ").append(String.join(", ", groups))
                    // Time in its own order; anything else by the measure, the biggest first
                    .append(grouping instanceof TimeFacet ? " ORDER BY " + String.join(", ", groups)
                            : " ORDER BY kpiValue DESC");
        }

        Query query = em.createQuery(jpql.toString());
        bind(query, jpql.toString(), period);
        parameters.forEach(query::setParameter);

        List<KpiValue> values = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] columns = groups.isEmpty() ? new Object[] { row } : (Object[]) row;
            Number measure = (Number) columns[columns.length - 1];
            FacetValue group = grouping == null ? null : grouping.key(groupLink, columns);
            values.add(new KpiValue(group, measure == null ? 0 : measure.doubleValue()));
        }
        return values;
    }

    // The period the query uses: :from and :to when it limits rows to it. A KPI with other parameters of its own
    // binds them too
    protected void bind(Query query, String jpql, Period period) {
        if (jpql.contains(":from")) {
            query.setParameter("from", period.from());
        }
        if (jpql.contains(":to")) {
            query.setParameter("to", period.to());
        }
    }

    // "Guia g LEFT JOIN g.origenLocalidad origin"
    private String from() {
        StringBuilder from = new StringBuilder();
        for (TableAlias table : tables()) {
            from.append(table.join() == null
                    ? table.table().entity() + " " + table.alias()
                    : " " + table.join());
        }
        return from.toString();
    }

    private Link resolve(Facet facet, String action) {
        Link link = facet.resolve(tables());
        if (link == null) {
            throw new IllegalArgumentException("The KPI " + id() + " cannot be " + action + " by " + facet.id());
        }
        return link;
    }
}

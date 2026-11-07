package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Period;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Table;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.TableAlias;

/**
 * The day each row happened: a trip on its date, a case on the day it was detected. The period is this facet's
 * filter; the granularity only changes how it groups. Its values are not a catalog but the days, weeks, months or
 * years of a period: buckets().
 */
public class TimeFacet extends Facet {

    public enum Granularity { DAY, WEEK, MONTH, YEAR }

    private final Granularity granularity;

    public TimeFacet(Granularity granularity) {
        super("time", Map.of(
                Table.TRIP, Link.of("{t}.fecha"),
                Table.CASE, Link.of("EXTRACT(DATE FROM {t}.fechaDeteccion)")));
        this.granularity = granularity;
    }

    // No fixed list: they depend on the period, see buckets()
    @Override
    public List<FacetValue> values(EntityManager em) {
        return List.of();
    }

    // A day, week, month or year of a period, with the days of it inside the period
    public record Bucket(FacetValue value, Period period) {
    }

    // Every day, week, month or year of the period, in order, with the ids key() gives the groups; the first and
    // last ones are cut to the period
    public List<Bucket> buckets(Period period) {
        Map<String, LocalDate[]> days = new LinkedHashMap<>();
        for (LocalDate day = period.from(); !day.isAfter(period.to()); day = day.plusDays(1)) {
            LocalDate current = day;
            days.computeIfAbsent(id(day), id -> new LocalDate[] { current, current })[1] = day;
        }
        List<Bucket> buckets = new ArrayList<>();
        days.forEach((id, range) -> buckets.add(new Bucket(new FacetValue(id, id), new Period(range[0], range[1]))));
        return buckets;
    }

    private String id(LocalDate day) {
        return switch (granularity) {
            case DAY -> day.toString();
            case WEEK -> String.format("%d-W%02d", day.getYear(), day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case MONTH -> String.format("%d-%02d", day.getYear(), day.getMonthValue());
            case YEAR -> String.valueOf(day.getYear());
        };
    }

    // The time of a KPI is the time of what it counts, never of a joined table. Companies and vehicles have no day
    // of their own, so they get none
    @Override
    public Link resolve(List<TableAlias> tables) {
        return super.resolve(tables.subList(0, 1));
    }

    @Override
    public List<String> groupBy(Link link) {
        String day = link.id();
        return switch (granularity) {
            case DAY -> List.of(day);
            case WEEK -> List.of("EXTRACT(YEAR FROM " + day + ")", "EXTRACT(WEEK FROM " + day + ")");
            case MONTH -> List.of("EXTRACT(YEAR FROM " + day + ")", "EXTRACT(MONTH FROM " + day + ")");
            case YEAR -> List.of("EXTRACT(YEAR FROM " + day + ")");
        };
    }

    // "2026-11-07", "2026-W45", "2026-11", "2026"
    @Override
    public FacetValue key(Link link, Object[] columns) {
        if (columns[0] == null) {
            return null;
        }
        String id = switch (granularity) {
            case DAY -> columns[0].toString();
            case WEEK -> String.format("%d-W%02d", number(columns[0]), number(columns[1]));
            case MONTH -> String.format("%d-%02d", number(columns[0]), number(columns[1]));
            case YEAR -> String.valueOf(number(columns[0]));
        };
        return new FacetValue(id, id);
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }
}

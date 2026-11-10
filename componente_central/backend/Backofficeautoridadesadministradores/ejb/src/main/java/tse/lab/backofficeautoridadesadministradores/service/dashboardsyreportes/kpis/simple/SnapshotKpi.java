package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Period;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Selection;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.Facet;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.TimeFacet;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.TimeFacet.Bucket;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.KpiValue;

/**
 * A picture on a day rather than events in a period: what there is on that day (vehicles enabled, companies
 * enabled). It ignores the start of the period and is limited by its end, :to: grouped by time, the end of each day,
 * week, month or year of the period, one query each; grouped by another facet, the end of the period.
 */
public abstract class SnapshotKpi extends BaseQueryKpi {

    protected SnapshotKpi(String id, String name) {
        super(id, name);
    }

    // Rows it keeps on the given day, a JPQL date expression: "v.activo = TRUE AND ... <= " + day
    protected abstract String condition(String day);

    @Override
    protected final String condition() {
        return condition(":to");
    }

    // Time has no link to its table: it is the day it is evaluated on
    @Override
    public boolean supports(Facet facet) {
        return facet instanceof TimeFacet || super.supports(facet);
    }

    @Override
    protected List<KpiValue> compute(EntityManager em, Period period, Facet grouping, Map<Facet, Selection> filters) {
        if (!(grouping instanceof TimeFacet time)) {
            return super.compute(em, period, grouping, filters);
        }
        return time.buckets(period).stream()
                .map(bucket -> new KpiValue(bucket.value(), onLastDay(em, bucket, filters)))
                .toList();
    }

    private double onLastDay(EntityManager em, Bucket bucket, Map<Facet, Selection> filters) {
        List<KpiValue> total = super.compute(em, bucket.period(), null, filters);
        return total.isEmpty() ? 0 : total.get(0).value();
    }
}

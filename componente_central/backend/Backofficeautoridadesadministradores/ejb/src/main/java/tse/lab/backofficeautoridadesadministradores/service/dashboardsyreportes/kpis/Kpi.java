package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Period;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Selection;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.Facet;

// An indicator of the reports: calculated from a query of its own (BaseQueryKpi) or from other KPIs (CompositeKpi)
public abstract class Kpi {

    private final String id;
    private final String name;

    protected Kpi(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    /**
     * The KPI over the period, one value per group of the facet. A report always groups by some facet, so a
     * missing grouping is an IllegalArgumentException, and so is a grouping or filter the KPI is not linked to.
     */
    public final List<KpiValue> calculate(EntityManager em, Period period, Facet grouping,
            Map<Facet, Selection> filters) {
        if (grouping == null) {
            throw new IllegalArgumentException("The KPI " + id + " needs a facet to group by");
        }
        return compute(em, period, grouping, filters == null ? Map.of() : filters);
    }

    // The calculation itself; grouping may be null here, for a KPI that needs its total on its own (a snapshot
    // takes one per day it is evaluated on)
    protected abstract List<KpiValue> compute(EntityManager em, Period period, Facet grouping,
            Map<Facet, Selection> filters);
}

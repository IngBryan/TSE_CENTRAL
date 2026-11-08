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

    /**
     * The KPI over the whole period, the only calculation without grouping: it is not the sum of the groups (a
     * rate is recalculated, a snapshot is taken on the last day, a company with two rubros is in two groups). Null
     * when it has no value, a rate without detected cases.
     */
    public final Double total(EntityManager em, Period period, Map<Facet, Selection> filters) {
        return computeTotal(em, period, filters == null ? Map.of() : filters);
    }

    // Whether calculate() takes the facet, to group by it or, unless it is time, to filter by it
    public abstract boolean supports(Facet facet);

    // The value of a group without rows: none of what it counts happened there
    public Double valueWithoutRows() {
        return 0.0;
    }

    // The calculation itself; grouping is null for the total (a snapshot also takes one per day it is evaluated on)
    protected abstract List<KpiValue> compute(EntityManager em, Period period, Facet grouping,
            Map<Facet, Selection> filters);

    protected Double computeTotal(EntityManager em, Period period, Map<Facet, Selection> filters) {
        List<KpiValue> values = compute(em, period, null, filters);
        return values.isEmpty() ? valueWithoutRows() : values.get(0).value();
    }
}

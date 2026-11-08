package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.composite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Period;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Selection;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.Facet;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.FacetValue;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.TimeFacet;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.Kpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.KpiValue;

/**
 * A KPI made of two others: the numerator over the denominator, group by group. It has no query of its own, so it
 * always agrees with the KPIs it is made of, and it takes the groupings and filters both of them take.
 */
public abstract class CompositeKpi extends Kpi {

    private final Kpi numerator;
    private final Kpi denominator;

    protected CompositeKpi(String id, String name, Kpi numerator, Kpi denominator) {
        super(id, name);
        this.numerator = numerator;
        this.denominator = denominator;
    }

    @Override
    public boolean supports(Facet facet) {
        return numerator.supports(facet) && denominator.supports(facet);
    }

    // A group the denominator has and the numerator lacks is a zero; one with a zero denominator has no value
    @Override
    protected List<KpiValue> compute(EntityManager em, Period period, Facet grouping, Map<Facet, Selection> filters) {
        Map<FacetValue, Double> numerators = new HashMap<>();
        numerator.calculate(em, period, grouping, filters).forEach(v -> numerators.put(v.group(), v.value()));

        List<KpiValue> values = new ArrayList<>();
        for (KpiValue base : denominator.calculate(em, period, grouping, filters)) {
            if (base.value() != 0) {
                values.add(new KpiValue(base.group(), numerators.getOrDefault(base.group(), 0.0) / base.value()));
            }
        }

        // Time keeps its order; anything else goes by the ratio, the biggest first
        if (!(grouping instanceof TimeFacet)) {
            values.sort(Comparator.comparingDouble(KpiValue::value).reversed());
        }
        return values;
    }

    // Recalculated over the whole period, never the sum or average of the groups
    @Override
    protected Double computeTotal(EntityManager em, Period period, Map<Facet, Selection> filters) {
        Double base = denominator.total(em, period, filters);
        if (base == null || base == 0) {
            return null;
        }
        Double part = numerator.total(em, period, filters);
        return (part == null ? 0 : part) / base;
    }

    // Nothing to divide: a group without rows has no rate
    @Override
    public Double valueWithoutRows() {
        return null;
    }
}

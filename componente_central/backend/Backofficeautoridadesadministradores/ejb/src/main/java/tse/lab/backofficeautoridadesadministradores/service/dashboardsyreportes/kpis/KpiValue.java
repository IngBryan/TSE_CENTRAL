package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.FacetValue;

// group is null for the total, or for the rows without a value of the facet (a case without a trip has no rubro)
public record KpiValue(FacetValue group, double value) {
}

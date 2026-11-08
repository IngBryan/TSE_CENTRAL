package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes;

// A facet a report can use. kind is "time" (only groups: the period is its filter) or "catalog" (groups and filters)
public record FacetDefinition(String id, String name, String kind) {
}

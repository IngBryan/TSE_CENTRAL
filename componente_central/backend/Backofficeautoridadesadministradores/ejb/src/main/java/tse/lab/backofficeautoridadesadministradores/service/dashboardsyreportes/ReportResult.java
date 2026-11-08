package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes;

import java.util.List;
import java.util.Map;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.FacetValue;

/**
 * One row per group, and the total. values go from a KPI id to its measures; a measure without value (the growth
 * from zero, a rate without cases) is null. group is null for the rows without a value of the facet.
 */
public record ReportResult(String groupBy, List<Row> rows, Map<String, Map<String, Double>> total) {

    public record Row(FacetValue group, Map<String, Map<String, Double>> values) {
    }
}

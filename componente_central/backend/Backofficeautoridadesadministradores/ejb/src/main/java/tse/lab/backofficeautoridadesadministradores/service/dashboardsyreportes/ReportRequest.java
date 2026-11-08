package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes;

import java.util.List;
import java.util.Map;

/**
 * What a report asks for. Dates are "2026-11-01T00:00:00" to "2026-11-07T23:59:59.999", Uruguay time, always whole
 * days: the front turns "this month" into them. comparison is needed only when a KPI asks for something other than
 * its current value. filters go from a facet id to the values it includes and excludes.
 */
public record ReportRequest(List<KpiRequest> kpis, Range period, Range comparison, String groupBy,
        Map<String, Selection> filters) {

    // measures: "current", "previous", "growth", "difference"
    public record KpiRequest(String id, List<String> measures) {
    }

    public record Range(String from, String to) {
    }
}

package tse.lab.backofficeautoridadesadministradores.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.FacetDefinition;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.FacetNotFoundException;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.InvalidReportException;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.KpiDefinition;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.KpiNotFoundException;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.ReportRequest;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.ReportResult;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.ReportService;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.FacetValue;

class ReportsResourceTest {

    private static final ReportRequest REQUEST = new ReportRequest(List.of(), null, null, "month", Map.of());

    private ReportsResource resource;
    private ReportService reports;
    private SecurityContext security;

    @BeforeEach
    void setUp() throws Exception {
        resource = new ReportsResource();
        reports = mock(ReportService.class);
        security = mock(SecurityContext.class);
        inject("reports", reports);
        inject("seguridad", security);
        when(security.getUserPrincipal()).thenReturn(() -> "88888888");
        when(security.isUserInRole("AUTORIDAD")).thenReturn(true);
    }

    private void inject(String name, Object value) throws Exception {
        Field field = ReportsResource.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(resource, value);
    }

    @Test
    void loadsTheReport() {
        ReportResult result = new ReportResult("month", List.of(), Map.of());
        when(reports.load(REQUEST)).thenReturn(result);

        Response response = resource.load(REQUEST);

        assertEquals(200, response.getStatus());
        assertSame(result, response.getEntity());
    }

    @Test
    void anInvalidReportIsA400WithItsDetail() {
        when(reports.load(REQUEST)).thenThrow(new InvalidReportException("Unknown KPI: ventas"));

        Response response = resource.load(REQUEST);

        assertEquals(400, response.getStatus());
        assertEquals(Map.of("error", "report_invalid", "detalle", "Unknown KPI: ventas"), response.getEntity());
    }

    @Test
    void listsTheKpis() {
        List<KpiDefinition> kpis = List.of(new KpiDefinition("viajes", "Viajes realizados"));
        when(reports.kpis()).thenReturn(kpis);

        Response response = resource.kpis();

        assertEquals(200, response.getStatus());
        assertSame(kpis, response.getEntity());
    }

    @Test
    void listsTheFacetsOfSeveralKpis() {
        List<String> kpis = List.of("viajes", "empresas");
        List<FacetDefinition> facets = List.of(new FacetDefinition("month", "Mes", "time"));
        when(reports.facets(kpis)).thenReturn(facets);

        Response response = resource.facets(kpis);

        assertEquals(200, response.getStatus());
        assertEquals(new ReportsResource.KpiFacetsResponse(kpis, facets), response.getEntity());
    }

    @Test
    void anUnknownKpiIsA404AndNoKpiA400() {
        when(reports.facets(List.of("ventas"))).thenThrow(new KpiNotFoundException("ventas"));
        when(reports.facets(List.of())).thenThrow(new InvalidReportException("Ask for the facets of at least one KPI"));

        assertEquals(404, resource.facets(List.of("ventas")).getStatus());
        assertEquals(400, resource.facets(List.of()).getStatus());
    }

    @Test
    void listsTheValuesOfAFacet() {
        List<FacetValue> values = List.of(new FacetValue("1", "Agropecuario"));
        when(reports.values("category")).thenReturn(values);

        Response response = resource.values("category");

        assertEquals(200, response.getStatus());
        assertEquals(new ReportsResource.FacetValuesResponse("category", values), response.getEntity());
    }

    @Test
    void anUnknownFacetIsA404AndTimeA400() {
        when(reports.values("color")).thenThrow(new FacetNotFoundException("color"));
        when(reports.values("month")).thenThrow(new InvalidReportException("The facet month has no fixed values"));

        assertEquals(404, resource.values("color").getStatus());
        assertEquals(400, resource.values("month").getStatus());
    }

    @Test
    void onlyAnAuthorityLoadsReports() {
        when(security.isUserInRole("AUTORIDAD")).thenReturn(false);

        assertEquals(403, resource.load(REQUEST).getStatus());
        assertEquals(403, resource.facets(List.of("viajes")).getStatus());
        assertEquals(403, resource.kpis().getStatus());
        assertEquals(403, resource.values("category").getStatus());
        verifyNoInteractions(reports);
    }
}

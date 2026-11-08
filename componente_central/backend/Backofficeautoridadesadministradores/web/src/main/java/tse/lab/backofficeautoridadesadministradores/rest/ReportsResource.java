package tse.lab.backofficeautoridadesadministradores.rest;

import java.util.List;
import java.util.Map;

import jakarta.ejb.EJB;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.FacetDefinition;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.FacetNotFoundException;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.InvalidReportException;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.KpiNotFoundException;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.ReportRequest;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.ReportService;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.FacetValue;

@Path("/reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReportsResource {

    @EJB
    private ReportService reports;

    @Context
    private SecurityContext seguridad;

    // The KPIs a report can ask for, with their id and name
    @GET
    @Path("/kpis")
    public Response kpis() {
        Response acceso = AccesoPorRol.verificar(seguridad, "AUTORIDAD");
        if (acceso != null) {
            return acceso;
        }
        return Response.ok(reports.kpis()).build();
    }

    // The facets a report of all the KPIs at once can be grouped and filtered by: ?kpi=viajes&kpi=empresas
    @GET
    @Path("/facets")
    public Response facets(@QueryParam("kpi") List<String> kpiIds) {
        Response acceso = AccesoPorRol.verificar(seguridad, "AUTORIDAD");
        if (acceso != null) {
            return acceso;
        }

        try {
            return Response.ok(new KpiFacetsResponse(kpiIds, reports.facets(kpiIds))).build();
        } catch (KpiNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "kpi_not_found", "detalle", e.getMessage()))
                    .build();
        } catch (InvalidReportException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "report_invalid", "detalle", e.getMessage()))
                    .build();
        }
    }

    // Every value of a catalog facet, to list in its filter
    @GET
    @Path("/facets/{facetId}/values")
    public Response values(@PathParam("facetId") String facetId) {
        Response acceso = AccesoPorRol.verificar(seguridad, "AUTORIDAD");
        if (acceso != null) {
            return acceso;
        }

        try {
            return Response.ok(new FacetValuesResponse(facetId, reports.values(facetId))).build();
        } catch (FacetNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "facet_not_found", "detalle", e.getMessage()))
                    .build();
        } catch (InvalidReportException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "facet_invalid", "detalle", e.getMessage()))
                    .build();
        }
    }

    // The front controls what can be asked for; anything else is a 400 with what was wrong
    @POST
    @Path("/load")
    public Response load(ReportRequest request) {
        Response acceso = AccesoPorRol.verificar(seguridad, "AUTORIDAD");
        if (acceso != null) {
            return acceso;
        }

        try {
            return Response.ok(reports.load(request)).build();
        } catch (InvalidReportException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "report_invalid", "detalle", e.getMessage()))
                    .build();
        }
    }

    public record FacetValuesResponse(String facetId, List<FacetValue> values) {
    }

    public record KpiFacetsResponse(List<String> kpis, List<FacetDefinition> facets) {
    }
}

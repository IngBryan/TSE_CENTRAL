package tse.lab.backofficeautoridadesadministradores.rest;

import java.util.List;
import java.util.Map;

import jakarta.ejb.EJB;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.DashboardFacetServiceLocal;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.DashboardFacetServiceLocal.FacetDefinition;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.FacetValue;

@Path("/dashboard")
@Produces(MediaType.APPLICATION_JSON)
public class DashboardResource {

    @EJB
    private DashboardFacetServiceLocal dashboardFacetService;

    @Context
    private SecurityContext seguridad;

    @GET
    @Path("/facets")
    public Response facets() {
        Response acceso = AccesoPorRol.verificar(seguridad, "AUTORIDAD");
        if (acceso != null) {
            return acceso;
        }

        List<FacetDefinition> facets = dashboardFacetService.facets();
        return Response.ok(facets).build();
    }

    @GET
    @Path("/facets/{facetId}/values")
    public Response values(@PathParam("facetId") String facetId) {
        Response acceso = AccesoPorRol.verificar(seguridad, "AUTORIDAD");
        if (acceso != null) {
            return acceso;
        }

        try {
            List<FacetValue> values = dashboardFacetService.values(facetId);
            return Response.ok(new FacetValuesResponse(facetId, values)).build();

        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "facet_invalid", "detalle", e.getMessage()))
                    .build();
        }
    }

    public record FacetValuesResponse(String facetId, List<FacetValue> values) {
    }
}

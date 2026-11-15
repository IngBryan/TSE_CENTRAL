package tse.lab.backofficeautoridadesadministradores.rest;

import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.math.BigDecimal;
import java.util.Map;
import tse.lab.backofficeautoridadesadministradores.service.descargos.*;

@Path("/plazo-descargo")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PlazoDescargoResource {
    @EJB private GestionPlazoDescargoService servicio;
    @Context private SecurityContext seguridad;
    @Context private jakarta.servlet.http.HttpServletRequest request;
    public record Cambio(BigDecimal revisionBase, BigDecimal plazoDescargoDias, String fundamento) { }

    private boolean autenticado() { return seguridad != null && seguridad.getUserPrincipal() != null; }
    @GET public Response obtener() {
        if (!autenticado()) return error(401, "Iniciá sesión para continuar");
        try { return Response.ok(servicio.obtener()).build(); }
        catch (PlazoDescargoException e) { return error(e.getStatus(), e.getMessage()); }
    }
    @PUT public Response actualizar(Cambio c) {
        if (!autenticado()) return error(401, "Iniciá sesión para continuar");
        if (c == null || c.revisionBase() == null || c.plazoDescargoDias() == null)
            return error(400, "Completá el plazo y la revisión de configuración");
        try {
            String id = request == null ? null : (String) request.getAttribute("X-Correlation-Id");
            return Response.ok(servicio.actualizar(c.revisionBase().longValueExact(),
                    c.plazoDescargoDias().intValueExact(), c.fundamento(), id)).build();
        } catch (PlazoDescargoException e) { return error(e.getStatus(), e.getMessage()); }
        catch (ArithmeticException e) { return error(400, "El plazo y la revisión deben ser enteros dentro del rango permitido"); }
    }
    private static Response error(int status, String mensaje) {
        return Response.status(status).entity(Map.of("detalle", mensaje)).build();
    }
}

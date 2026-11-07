package tse.lab.backofficeautoridadesadministradores.rest;

import jakarta.ejb.EJB;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.math.BigDecimal;
import java.util.Map;
import tse.lab.backofficeautoridadesadministradores.service.reglas.*;

@Path("/reglas-fiscalizacion")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReglasFiscalizacionResource {
    @EJB private GestionReglasService servicio;
    @Context private SecurityContext seguridad;
    @Context private jakarta.servlet.http.HttpServletRequest request;
    public record Cambio(BigDecimal revisionBase, BigDecimal toleranciaCargaPorcentaje, BigDecimal toleranciaCargaKg,
            BigDecimal distanciaDesvioMetros, BigDecimal ventanaDesvioSegundos, String fundamento) { }
    private Response acceso() {
        return seguridad == null || seguridad.getUserPrincipal() == null
                ? Response.status(401).entity(Map.of("detalle", "Iniciá sesión para continuar")).build() : null;
    }
    @GET public Response obtener() {
        if (acceso()!=null) return acceso();
        try { return Response.ok(servicio.obtener()).build(); } catch (ReglasException e) { return error(e); }
    }
    @GET @Path("/historial") public Response historial() {
        if (acceso()!=null) return acceso();
        try { return Response.ok(servicio.historial()).build(); } catch (ReglasException e) { return error(e); }
    }
    @PUT public Response actualizar(Cambio c) {
        if (acceso()!=null) return acceso();
        if (c==null || c.revisionBase()==null || c.toleranciaCargaPorcentaje()==null || c.toleranciaCargaKg()==null
                || c.distanciaDesvioMetros()==null || c.ventanaDesvioSegundos()==null)
            return Response.status(400).entity(Map.of("detalle", "Completá todos los parámetros")).build();
        try {
            String id=request == null ? null : (String) request.getAttribute("X-Correlation-Id");
            return Response.ok(servicio.actualizar(c.revisionBase().longValueExact(), c.toleranciaCargaPorcentaje(), c.toleranciaCargaKg().intValueExact(),
                    c.distanciaDesvioMetros().intValueExact(), c.ventanaDesvioSegundos().intValueExact(), c.fundamento(), id)).build();
        } catch (ReglasException e) { return error(e); }
        catch (ArithmeticException e) {
            return Response.status(400).entity(Map.of("detalle", "Revisión, kg, metros y segundos deben ser enteros dentro del rango permitido")).build();
        }
    }
    private static Response error(ReglasException e) {
        return Response.status(e.getStatus()).entity(Map.of("detalle", e.getMessage())).build();
    }
}

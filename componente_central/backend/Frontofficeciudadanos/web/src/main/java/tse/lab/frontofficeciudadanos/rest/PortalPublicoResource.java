package tse.lab.frontofficeciudadanos.rest;

import static tse.lab.frontofficeciudadanos.rest.GuiasResource.correlationId;
import static tse.lab.frontofficeciudadanos.rest.GuiasResource.ejecutar;

import jakarta.ejb.EJB;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tse.lab.frontofficeciudadanos.service.portalpublico.PortalPublicoService;

// Portal publico: no hay usuario (sin X-Usuario-Ci), pero la API sigue exigiendo la X-Api-Key del front de
// Next (ApiKeyFilter): el navegador no llama a WildFly directo. Errores como en GuiasResource: 400 y 404.
@Path("/publico")
@Produces(MediaType.APPLICATION_JSON)
public class PortalPublicoResource {

    @EJB
    private PortalPublicoService portal;

    // ?nombre=este (3 a 60 caracteres)
    @GET
    @Path("/empresas")
    public Response buscar(@QueryParam("nombre") String nombre, @Context HttpServletRequest req) {
        return ejecutar(() -> portal.buscarEmpresas(nombre, correlationId(req)));
    }

    // ?matricula=SBA1234 (opcional)
    @GET
    @Path("/empresas/{nro}")
    public Response validar(@PathParam("nro") Long nro, @QueryParam("matricula") String matricula,
            @Context HttpServletRequest req) {
        return ejecutar(() -> portal.validarEmpresa(nro, matricula, correlationId(req)));
    }

    // ?anio=2026 (por defecto el actual). anio llega como texto: con Integer, un valor no numerico daria 404.
    @GET
    @Path("/estadisticas")
    public Response estadisticas(@QueryParam("anio") String anio, @Context HttpServletRequest req) {
        return ejecutar(() -> portal.estadisticas(anio == null || anio.isBlank() ? null : anioNumerico(anio),
                correlationId(req)));
    }

    private static Integer anioNumerico(String anio) {
        return anio.matches("\\d{4}") ? Integer.valueOf(anio) : Integer.valueOf(0);
    }
}

package tse.lab.frontofficeciudadanos.rest;

import java.util.Map;
import jakarta.ejb.EJB;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tse.lab.frontofficeciudadanos.service.rutas.RutasException;
import tse.lab.frontofficeciudadanos.service.rutas.RutasGuiaService;

/** Endpoint separado del CRUD de Guías para evitar conflictos con el frontend de Ciudadanos. */
@Path("/empresa/guias/{id}/rutas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GuiasRutasResource {
    public record Confirmacion(Long origenId, Long destinoId, String previsualizacionId) { }

    @EJB private RutasGuiaService rutas;

    @GET
    public Response consultar(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario,
            @PathParam("id") String id, @Context HttpServletRequest req) {
        return ejecutar(() -> rutas.consultar(usuario, id, GuiasResource.correlationId(req)));
    }

    @POST
    public Response confirmar(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario,
            @PathParam("id") String id, Confirmacion body, @Context HttpServletRequest req) {
        if (body == null) {
            return Response.status(400).entity(Map.of("error", "solicitud_invalida")).build();
        }
        return ejecutar(() -> rutas.confirmar(usuario, id, body.origenId(), body.destinoId(),
                body.previsualizacionId(), GuiasResource.correlationId(req)));
    }

    private static Response ejecutar(GuiasResource.Operacion op) {
        try {
            return GuiasResource.ejecutar(op);
        } catch (RutasException e) {
            return Response.status(e.status()).entity(Map.of("error", e.codigo(), "detalle", e.getMessage())).build();
        } catch (IllegalStateException e) {
            return Response.status(409).entity(Map.of("error", "viaje_no_modificable", "detalle", e.getMessage())).build();
        }
    }
}

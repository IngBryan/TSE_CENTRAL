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
import tse.lab.frontofficeciudadanos.servicio.ConflictoException;
import tse.lab.frontofficeciudadanos.servicio.DatosInvalidosException;
import tse.lab.frontofficeciudadanos.servicio.GuiasService;
import tse.lab.frontofficeciudadanos.servicio.GuiasService.NuevaGuia;
import tse.lab.frontofficeciudadanos.servicio.NoAutorizadoException;
import tse.lab.frontofficeciudadanos.servicio.RecursoNoEncontradoException;

// Gestion de Viajes de Carga y CU-07 Asignar vehiculo y chofer a guia de viaje. Quien llama se
// identifica con X-Usuario-Ci (ver ChoferesResource para el criterio de confianza).
@Path("/empresa/guias")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class GuiasResource {

    public record AsignacionRequest(String matricula, Long idChofer) {
    }

    @EJB
    private GuiasService guias;

    @GET
    public Response listar(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario, @Context HttpServletRequest req) {
        return ejecutar(() -> guias.listar(usuario, correlationId(req)));
    }

    @POST
    public Response crear(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario, NuevaGuia datos,
            @Context HttpServletRequest req) {
        return ejecutar(() -> guias.crear(usuario, datos, correlationId(req)));
    }

    @GET
    @Path("/{id}/asignacion")
    public Response opciones(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario, @PathParam("id") String id,
            @Context HttpServletRequest req) {
        return ejecutar(() -> guias.opcionesAsignacion(usuario, id, correlationId(req)));
    }

    @POST
    @Path("/{id}/asignacion")
    public Response asignar(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario, @PathParam("id") String id,
            AsignacionRequest body, @Context HttpServletRequest req) {
        return ejecutar(() -> guias.asignar(usuario, id, body == null ? null : body.matricula(),
                body == null ? null : body.idChofer(), correlationId(req)));
    }

    interface Operacion {
        Object ejecutar() throws Exception;
    }

    // Traduce las excepciones de negocio a 400 / 403 / 404 / 409
    private static Response ejecutar(Operacion op) {
        try {
            return Response.ok(op.ejecutar()).build();
        } catch (DatosInvalidosException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "datos_invalidos", "campos", e.getErrores())).build();
        } catch (NoAutorizadoException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "no_autorizado", "detalle", e.getMessage())).build();
        } catch (RecursoNoEncontradoException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "no_encontrada", "detalle", e.getMessage())).build();
        } catch (ConflictoException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", e.getCodigo(), "detalle", e.getMessage())).build();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String correlationId(HttpServletRequest req) {
        return (String) req.getAttribute(ApiKeyFilter.CORRELATION_ID);
    }
}

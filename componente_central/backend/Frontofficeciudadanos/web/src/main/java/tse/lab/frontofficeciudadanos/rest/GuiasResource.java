package tse.lab.frontofficeciudadanos.rest;

import java.util.Map;

import jakarta.ejb.EJB;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tse.lab.frontofficeciudadanos.servicio.ConflictoException;
import tse.lab.frontofficeciudadanos.servicio.DatosInvalidosException;
import tse.lab.frontofficeciudadanos.servicio.GuiasService;
import tse.lab.frontofficeciudadanos.servicio.GuiasService.Cancelacion;
import tse.lab.frontofficeciudadanos.servicio.GuiasService.EdicionGuia;
import tse.lab.frontofficeciudadanos.servicio.GuiasService.NuevaGuia;
import tse.lab.frontofficeciudadanos.servicio.GuiasService.Reasignacion;
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

    // Modificacion de una guia ASIGNADA (409 GUIA_NO_MODIFICABLE si no; 409 GUIA_DESACTUALIZADA si la
    // version del body no es la actual). Cada cambio queda en la linea de tiempo de la guia.
    @PUT
    @Path("/{id}")
    public Response editar(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario, @PathParam("id") String id,
            EdicionGuia body, @Context HttpServletRequest req) {
        return ejecutar(() -> guias.editar(usuario, id, body, correlationId(req)));
    }

    @POST
    @Path("/{id}/reasignacion")
    public Response reasignar(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario, @PathParam("id") String id,
            Reasignacion body, @Context HttpServletRequest req) {
        return ejecutar(() -> guias.reasignar(usuario, id, body, correlationId(req)));
    }

    @POST
    @Path("/{id}/cancelacion")
    public Response cancelar(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario, @PathParam("id") String id,
            Cancelacion body, @Context HttpServletRequest req) {
        return ejecutar(() -> guias.cancelar(usuario, id, body, correlationId(req)));
    }

    // Detalle de la guia con los eventos que registro el chofer desde la app movil y las modificaciones
    @GET
    @Path("/{id}/eventos")
    public Response eventos(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario, @PathParam("id") String id,
            @Context HttpServletRequest req) {
        return ejecutar(() -> guias.eventos(usuario, id, correlationId(req)));
    }

    // Foto adjunta a un evento (incidente): JPEG o PNG tal como la mando el movil
    @GET
    @Path("/{id}/eventos/{idEvento}/foto")
    @Produces({ "image/jpeg", "image/png", MediaType.APPLICATION_JSON })
    public Response foto(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario, @PathParam("id") String id,
            @PathParam("idEvento") String idEvento, @Context HttpServletRequest req) {
        Response r = ejecutar(() -> guias.fotoEvento(usuario, id, idEvento, correlationId(req)));
        if (r.getStatus() != 200) {
            return Response.fromResponse(r).type(MediaType.APPLICATION_JSON).build();
        }
        if (!(r.getEntity() instanceof byte[] foto)) {
            return Response.status(Response.Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", "no_encontrada", "detalle", "El evento no tiene foto")).build();
        }
        boolean png = foto.length > 1 && (foto[0] & 0xFF) == 0x89;
        return Response.ok(foto, png ? "image/png" : "image/jpeg").header("Cache-Control", "private, max-age=3600")
                .build();
    }

    interface Operacion {
        Object ejecutar() throws Exception;
    }

    // Traduce las excepciones de negocio a 400 / 403 / 404 / 409
    static Response ejecutar(Operacion op) {
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

    static String correlationId(HttpServletRequest req) {
        return (String) req.getAttribute(ApiKeyFilter.CORRELATION_ID);
    }
}

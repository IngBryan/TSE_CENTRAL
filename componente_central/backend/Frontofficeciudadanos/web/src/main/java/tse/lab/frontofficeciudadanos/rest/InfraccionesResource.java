package tse.lab.frontofficeciudadanos.rest;

import static tse.lab.frontofficeciudadanos.rest.GuiasResource.correlationId;
import static tse.lab.frontofficeciudadanos.rest.GuiasResource.ejecutar;

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
import tse.lab.frontofficeciudadanos.service.infraccionesydescargos.InfraccionesService;
import tse.lab.frontofficeciudadanos.service.infraccionesydescargos.InfraccionesService.NuevoDescargo;

// Gestion de Infracciones de la empresa del responsable: listado, detalle con la evidencia, descargo y las
// notificaciones de casos (marcar una o todas como vistas).
// Quien llama se identifica con X-Usuario-Ci (ver ChoferesResource). Errores como en GuiasResource: 400,
// 403, 404 y 409 (DESCARGO_YA_PRESENTADO, TRANSICION_NO_PERMITIDA, PLAZO_VENCIDO).
@Path("/empresa/infracciones")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class InfraccionesResource {

    @EJB
    private InfraccionesService infracciones;

    @GET
    public Response listar(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario, @Context HttpServletRequest req) {
        return ejecutar(() -> infracciones.listar(usuario, correlationId(req)));
    }

    // Campana del encabezado: {noVistas, notificaciones: [...]}
    @GET
    @Path("/notificaciones")
    public Response notificaciones(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario,
            @Context HttpServletRequest req) {
        return ejecutar(() -> infracciones.notificaciones(usuario, correlationId(req)));
    }

    @POST
    @Path("/notificaciones/vistas")
    public Response marcarTodasVistas(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario,
            @Context HttpServletRequest req) {
        return ejecutar(() -> infracciones.marcarTodasVistas(usuario, correlationId(req)));
    }

    @POST
    @Path("/{id}/vista")
    public Response marcarVista(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario, @PathParam("id") Long id,
            @Context HttpServletRequest req) {
        return ejecutar(() -> infracciones.marcarVista(usuario, id, correlationId(req)));
    }

    @GET
    @Path("/{id}")
    public Response detalle(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario, @PathParam("id") Long id,
            @Context HttpServletRequest req) {
        return ejecutar(() -> infracciones.detalle(usuario, id, correlationId(req)));
    }

    // Body {"descripcion": "..."}
    @POST
    @Path("/{id}/descargo")
    public Response descargo(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario, @PathParam("id") Long id,
            NuevoDescargo datos, @Context HttpServletRequest req) {
        return ejecutar(() -> infracciones.presentarDescargo(usuario, id, datos, correlationId(req)));
    }
}

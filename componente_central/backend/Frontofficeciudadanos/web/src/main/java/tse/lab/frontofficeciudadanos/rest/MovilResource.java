package tse.lab.frontofficeciudadanos.rest;

import jakarta.ejb.EJB;
import jakarta.ejb.EJBException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tse.lab.frontofficeciudadanos.servicio.ViajesChoferService;
import tse.lab.frontofficeciudadanos.servicio.ViajesChoferService.NuevoEvento;
import tse.lab.frontofficeciudadanos.servicio.ViajesChoferService.ResultadoEvento;

// API de la app movil del chofer. No la llama el movil directamente: pasa por el servidor de Next
// (BFF), que valida el token del movil y manda la cedula en X-Usuario-Ci junto con la X-Api-Key
// (mismo criterio de confianza que ChoferesResource).
//
// Respuestas de POST /eventos (politica de conflictos, TSE_MOVIL/docs/adr/0002):
// - 200 con resultado APLICADO o SIN_EFECTO (el efecto ya estaba aplicado): el movil lo marca SINCRONIZADO.
// - 409 con resultado CONFLICTO: el evento quedo registrado pero no cambio la guia (p. ej. carga despues
//   de FIN, guia reasignada o cancelada). El movil lo marca CONFLICTO y no lo reintenta.
// - Un reenvio del mismo idEvento devuelve lo mismo que el envio original, con duplicado=true.
// - 400 datos_invalidos, 403 no_autorizado, 404 no_encontrada y 409 ID_EVENTO_EN_USO: no se guarda
//   nada; el movil lo marca RECHAZADO y no lo reintenta. Un 5xx es transitorio: el movil reintenta.
@Path("/movil")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class MovilResource {

    @EJB
    private ViajesChoferService viajes;

    @GET
    @Path("/viajes")
    public Response viajes(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario, @Context HttpServletRequest req) {
        return GuiasResource.ejecutar(() -> viajes.viajes(usuario, GuiasResource.correlationId(req)));
    }

    @POST
    @Path("/eventos")
    public Response evento(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario, NuevoEvento body,
            @Context HttpServletRequest req) {
        Response r;
        try {
            r = GuiasResource.ejecutar(() -> viajes.registrarEvento(usuario, body, GuiasResource.correlationId(req)));
        } catch (EJBException e) {
            // Dos envios simultaneos del mismo evento: el segundo choca con la clave primaria al
            // insertar. Si el evento quedo guardado por el otro envio, se responde como reenvio.
            ResultadoEvento existente = body == null ? null : viajes.resultadoExistente(usuario, body.idEvento());
            if (existente == null) {
                throw e;
            }
            r = Response.ok(existente).build();
        }
        if (r.getEntity() instanceof ResultadoEvento re && "CONFLICTO".equals(re.resultado())) {
            return Response.status(Response.Status.CONFLICT).entity(re).build();
        }
        return r;
    }
}

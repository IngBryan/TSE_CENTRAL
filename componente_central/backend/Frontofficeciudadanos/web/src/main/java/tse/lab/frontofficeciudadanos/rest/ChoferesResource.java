package tse.lab.frontofficeciudadanos.rest;

import java.util.Map;
import java.util.regex.Pattern;

import jakarta.ejb.EJB;
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
import tse.lab.frontofficeciudadanos.service.gestionchoferes.AltaChoferService;
import tse.lab.frontofficeciudadanos.service.NoAutorizadoException;

// CU-02 Dar de alta chofer en la empresa. Lo consume el frontoffice de Next.
//
// Quien llama se identifica con el header X-Usuario-Ci: la cedula de la sesion del front. El
// backend confia en ese valor porque la API exige la clave compartida (X-Api-Key) que solo tiene
// el servidor de Next, y es ese servidor el que lee la cedula de su cookie de sesion cifrada.
// Si la API se expusiera a otros clientes habria que reemplazarlo por un token firmado (por
// ejemplo el id_token de gub.uy validado aca). Con esa cedula, AltaChoferService controla que
// sea responsable validado de una empresa habilitada.
@Path("/empresa/choferes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ChoferesResource {

    static final String USUARIO_CI = "X-Usuario-Ci";
    private static final Pattern CEDULA = Pattern.compile("\\d{6,8}");

    public record CedulaRequest(String cedula) {
    }

    @EJB
    private AltaChoferService altaChofer;

    @GET
    public Response listar(@HeaderParam(USUARIO_CI) String usuario, @Context HttpServletRequest req) {
        try {
            return Response.ok(altaChofer.listar(usuario, correlationId(req))).build();
        } catch (NoAutorizadoException e) {
            return prohibido(e);
        }
    }

    // Pasos 2 a 7: consulta la cedula y devuelve primer nombre y primer apellido para confirmar.
    @POST
    @Path("/consulta")
    public Response consultar(@HeaderParam(USUARIO_CI) String usuario, CedulaRequest body,
            @Context HttpServletRequest req) {
        if (!cedulaValida(body)) {
            return cedulaInvalida();
        }
        try {
            return Response.ok(altaChofer.consultar(usuario, body.cedula(), correlationId(req))).build();
        } catch (NoAutorizadoException e) {
            return prohibido(e);
        }
    }

    // Pasos 8 a 10: el responsable confirmo el nombre.
    @POST
    public Response confirmar(@HeaderParam(USUARIO_CI) String usuario, CedulaRequest body,
            @Context HttpServletRequest req) {
        if (!cedulaValida(body)) {
            return cedulaInvalida();
        }
        try {
            return Response.ok(altaChofer.confirmar(usuario, body.cedula(), correlationId(req))).build();
        } catch (NoAutorizadoException e) {
            return prohibido(e);
        }
    }

    private static boolean cedulaValida(CedulaRequest body) {
        return body != null && body.cedula() != null && CEDULA.matcher(body.cedula()).matches();
    }

    private static Response cedulaInvalida() {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "cedula_invalida", "detalle", "La cedula debe tener de 6 a 8 digitos"))
                .build();
    }

    private static Response prohibido(NoAutorizadoException e) {
        return Response.status(Response.Status.FORBIDDEN)
                .entity(Map.of("error", "no_autorizado", "detalle", e.getMessage()))
                .build();
    }

    private static String correlationId(HttpServletRequest req) {
        return (String) req.getAttribute(ApiKeyFilter.CORRELATION_ID);
    }
}

package tse.lab.frontofficeciudadanos.rest;

import java.util.Map;

import jakarta.ejb.EJB;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tse.lab.frontofficeciudadanos.servicio.DatosInvalidosException;
import tse.lab.frontofficeciudadanos.servicio.NoAutorizadoException;
import tse.lab.frontofficeciudadanos.servicio.PerfilEmpresaService;
import tse.lab.frontofficeciudadanos.servicio.PerfilEmpresaService.CambiosEmpresa;

// Perfil de la Empresa: el responsable consulta y actualiza los datos de su empresa. Quien llama se
// identifica con X-Usuario-Ci (ver ChoferesResource para el criterio de confianza).
@Path("/empresa")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EmpresaResource {

    @EJB
    private PerfilEmpresaService perfilEmpresa;

    @GET
    public Response obtener(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario, @Context HttpServletRequest req) {
        try {
            return Response.ok(perfilEmpresa.obtener(usuario, correlationId(req))).build();
        } catch (NoAutorizadoException e) {
            return prohibido(e);
        }
    }

    @PUT
    public Response actualizar(@HeaderParam(ChoferesResource.USUARIO_CI) String usuario, CambiosEmpresa cambios,
            @Context HttpServletRequest req) {
        try {
            return Response.ok(perfilEmpresa.actualizar(usuario, cambios, correlationId(req))).build();
        } catch (NoAutorizadoException e) {
            return prohibido(e);
        } catch (DatosInvalidosException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "datos_invalidos", "campos", e.getErrores()))
                    .build();
        }
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

package tse.lab.frontofficefuncionarios.rest;

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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tse.lab.frontofficefuncionarios.service.ConflictoException;
import tse.lab.frontofficefuncionarios.service.DatosInvalidosException;
import tse.lab.frontofficefuncionarios.service.NoAutorizadoException;
import tse.lab.frontofficefuncionarios.service.RecursoNoEncontradoException;
import tse.lab.frontofficefuncionarios.service.gestioncasos.GestionCasosService;
import tse.lab.frontofficefuncionarios.service.gestioncasos.GestionCasosService.Resolucion;

// CU Gestionar casos de fiscalizacion. Quien llama se identifica con X-Usuario-Ci: la cedula que el
// frontoffice de Next obtuvo del login con gub.uy. Se confia en ella porque solo el front conoce la
// X-Api-Key (ApiKeyFilter); la autorizacion (que sea funcionario) la hace el servicio.
@Path("/casos")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CasosResource {

    static final String USUARIO_CI = "X-Usuario-Ci";

    @EJB
    private GestionCasosService casos;

    // Bandeja de trabajo; ?estado=EN_DESCARGO filtra por estado
    @GET
    public Response bandeja(@HeaderParam(USUARIO_CI) String usuario, @QueryParam("estado") String estado,
            @Context HttpServletRequest req) {
        return ejecutar(() -> casos.bandeja(usuario, estado, correlationId(req)));
    }

    @GET
    @Path("/{id}")
    public Response detalle(@HeaderParam(USUARIO_CI) String usuario, @PathParam("id") Long id,
            @Context HttpServletRequest req) {
        return ejecutar(() -> casos.detalle(usuario, id, correlationId(req)));
    }

    @GET
    @Path("/{id}/evidencia")
    public Response evidencia(@HeaderParam(USUARIO_CI) String usuario, @PathParam("id") Long id,
            @Context HttpServletRequest req) {
        return ejecutar(() -> casos.evidencia(usuario, id, correlationId(req)));
    }

    // 404 SIN_DESCARGO si la empresa no presento descargo
    @GET
    @Path("/{id}/descargo")
    public Response descargo(@HeaderParam(USUARIO_CI) String usuario, @PathParam("id") Long id,
            @Context HttpServletRequest req) {
        return ejecutar(() -> casos.descargo(usuario, id, correlationId(req)));
    }

    // Body {"decision": "CONFIRMAR" | "DESESTIMAR", "fundamento": "..."}.
    // 409 TRANSICION_NO_PERMITIDA si el caso no esta en un estado que admita resolucion.
    @POST
    @Path("/{id}/resolucion")
    public Response resolver(@HeaderParam(USUARIO_CI) String usuario, @PathParam("id") Long id, Resolucion body,
            @Context HttpServletRequest req) {
        return ejecutar(() -> casos.resolver(usuario, id, body, correlationId(req)));
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
                    .entity(Map.of("error", e.getCodigo(), "detalle", e.getMessage())).build();
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

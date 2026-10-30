package tse.lab.frontofficefuncionarios.rest;

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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tse.lab.frontofficefuncionarios.service.ConflictoException;
import tse.lab.frontofficefuncionarios.service.DatosInvalidosException;
import tse.lab.frontofficefuncionarios.service.NoAutorizadoException;
import tse.lab.frontofficefuncionarios.service.RecursoNoEncontradoException;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService.AltaEmpresa;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService.AsociacionUsuario;
import tse.lab.frontofficefuncionarios.service.gestiondeempresas.GestionEmpresasService.CambiosEmpresa;

// CU Gestionar empresas de transporte. Igual que CasosResource: quien llama se identifica con
// X-Usuario-Ci y la autorizacion (que sea funcionario) la hace el servicio.
@Path("/empresas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EmpresasResource {

    static final String USUARIO_CI = "X-Usuario-Ci";

    @EJB
    private GestionEmpresasService empresas;

    @GET
    public Response listar(@HeaderParam(USUARIO_CI) String usuario, @Context HttpServletRequest req) {
        return ejecutar(() -> empresas.listar(usuario, correlationId(req)));
    }

    // Usuarios que se pueden elegir como responsable (validados y sin empresa). ?ci=4567 filtra por el
    // comienzo de la cedula. Ruta fija: JAX-RS la prefiere a /{nro}.
    @GET
    @Path("/usuarios-disponibles")
    public Response usuariosDisponibles(@HeaderParam(USUARIO_CI) String usuario, @QueryParam("ci") String filtro,
            @Context HttpServletRequest req) {
        return ejecutar(() -> empresas.usuariosDisponibles(usuario, filtro, correlationId(req)));
    }

    // Body {"nroEmpresa": 2, "nombrePublico": "...", "razonSocial": "...", "direccion": "...",
    // "habilitada": false, "ciResponsable": "12345678"}; habilitada y ciResponsable son opcionales.
    // 409 EMPRESA_EXISTENTE si el numero ya esta registrado; con ciResponsable, los mismos errores que la
    // asociacion (404 USUARIO_NO_ENCONTRADO, 409 USUARIO_NO_VALIDADO o RESPONSABLE_DE_OTRA_EMPRESA).
    @POST
    public Response alta(@HeaderParam(USUARIO_CI) String usuario, AltaEmpresa body, @Context HttpServletRequest req) {
        return ejecutar(() -> empresas.alta(usuario, body, correlationId(req)));
    }

    @GET
    @Path("/{nro}")
    public Response detalle(@HeaderParam(USUARIO_CI) String usuario, @PathParam("nro") Long nro,
            @Context HttpServletRequest req) {
        return ejecutar(() -> empresas.detalle(usuario, nro, correlationId(req)));
    }

    // Body {"nombrePublico": "...", "razonSocial": "...", "direccion": "...", "habilitada": true, "version": 3};
    // habilitada es opcional. 409 EMPRESA_DESACTUALIZADA si version no es la actual de la empresa.
    @PUT
    @Path("/{nro}")
    public Response modificar(@HeaderParam(USUARIO_CI) String usuario, @PathParam("nro") Long nro,
            CambiosEmpresa body, @Context HttpServletRequest req) {
        return ejecutar(() -> empresas.modificar(usuario, nro, body, correlationId(req)));
    }

    // Body {"ci": "12345678"}: asocia al usuario (registrado y validado) como responsable de la empresa.
    // 404 USUARIO_NO_ENCONTRADO; 409 USUARIO_NO_VALIDADO, EMPRESA_CON_RESPONSABLE o RESPONSABLE_DE_OTRA_EMPRESA.
    @POST
    @Path("/{nro}/responsable")
    public Response asociarUsuario(@HeaderParam(USUARIO_CI) String usuario, @PathParam("nro") Long nro,
            AsociacionUsuario body, @Context HttpServletRequest req) {
        return ejecutar(() -> empresas.asociarUsuario(usuario, nro, body, correlationId(req)));
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

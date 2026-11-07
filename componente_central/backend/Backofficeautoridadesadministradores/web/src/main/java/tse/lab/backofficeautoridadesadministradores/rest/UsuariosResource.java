package tse.lab.backofficeautoridadesadministradores.rest;

import java.util.Map;
import java.util.function.Function;

import jakarta.ejb.EJB;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.AltaUsuarioService;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.BajaNoPermitidaException;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.BajaUsuarioService;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.DatosUsuarioInvalidosException;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.CambioRolNoPermitidoException;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.CambioRolService;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.GestionUsuariosService;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.PaginaInvalidaException;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.UsuarioExistenteException;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.UsuarioNoEncontradoException;

/**
 * API REST de los usuarios del sistema (listado, alta, cambio de rol y
 * baja), solo para administradores.
 */
@Path("/usuarios")
@Produces(MediaType.APPLICATION_JSON)
public class UsuariosResource {

    @EJB
    private GestionUsuariosService servicio;

    @EJB
    private AltaUsuarioService alta;

    @EJB
    private CambioRolService cambioRol;

    @EJB
    private BajaUsuarioService baja;

    @Context
    private SecurityContext seguridad;

    /**
     * Elimina a un funcionario, administrador o autoridad (todos esos roles).
     * Un funcionario queda inactivo, con su historial. Responde 200, 400 si
     * la cédula no es válida, 404 si no tiene ninguno de esos roles o 409 si
     * es el propio administrador.
     */
    @DELETE
    @Path("/{ci}")
    public Response eliminar(@PathParam("ci") String ci) {
        return comoAdministrador(administrador -> Response.ok(baja.eliminar(administrador, ci)).build());
    }

    // contrasena: administrador y autoridad; email: funcionario
    public record AltaUsuarioRequest(
            String tipo,
            String ci,
            String contrasena,
            String email) {
    }

    // contrasena: si pasa a administrador o autoridad sin serlo; email: si pasa a funcionario sin serlo
    public record CambioRolRequest(
            String rol,
            String contrasena,
            String email) {
    }

    /**
     * Reemplaza el rol de un funcionario, administrador o autoridad por
     * otro de esos tres. Responde 200, 400 si los datos no son válidos, 404
     * si la cédula no tiene ninguno de esos roles o 409 si ya tiene el rol
     * pedido, es el propio administrador o la cédula se dio de alta a la vez.
     */
    @PUT
    @Path("/{ci}/rol")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response cambiarRol(@PathParam("ci") String ci, CambioRolRequest datos) {
        return comoAdministrador(administrador -> datos == null
                ? sinCuerpo()
                : Response.ok(cambioRol.cambiar(
                        administrador, ci, datos.rol(), datos.contrasena(), datos.email())).build());
    }

    /**
     * Obtiene una página de usuarios del sistema, opcionalmente filtrada
     * por rol y por el comienzo de la cédula. Los parámetros son
     * opcionales; si no son válidos responde 400.
     */
    @GET
    public Response listar(
            @QueryParam("pagina") String pagina,
            @QueryParam("tamanio") String tamanio,
            @QueryParam("rol") String rol,
            @QueryParam("ci") String ci) {

        return comoAdministrador(administrador -> Response.ok(servicio.listar(pagina, tamanio, rol, ci)).build());
    }

    /**
     * Da de alta un administrador, una autoridad o un funcionario.
     * Responde 201, 400 si los datos no son válidos o 409 si la cédula ya
     * tiene ese usuario.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response crear(AltaUsuarioRequest datos) {
        return comoAdministrador(administrador -> datos == null
                ? sinCuerpo()
                : Response.status(Response.Status.CREATED)
                        .entity(alta.crear(administrador, datos.tipo(), datos.ci(), datos.contrasena(), datos.email()))
                        .build());
    }

    // Verifica el rol y traduce las excepciones de los servicios a su estado HTTP, para todas las operaciones
    private Response comoAdministrador(Function<String, Response> operacion) {
        Response acceso = AccesoAdministrador.verificar(seguridad);

        if (acceso != null) {
            return acceso;
        }

        try {
            return operacion.apply(seguridad.getUserPrincipal().getName());

        } catch (DatosUsuarioInvalidosException | PaginaInvalidaException e) {
            return error(Response.Status.BAD_REQUEST, "datos_invalidos", e.getMessage());

        } catch (UsuarioNoEncontradoException e) {
            return error(Response.Status.NOT_FOUND, "usuario_no_encontrado", e.getMessage());

        } catch (UsuarioExistenteException e) {
            return error(Response.Status.CONFLICT, "usuario_existente", e.getMessage());

        } catch (CambioRolNoPermitidoException e) {
            return error(Response.Status.CONFLICT, "cambio_no_permitido", e.getMessage());

        } catch (BajaNoPermitidaException e) {
            return error(Response.Status.CONFLICT, "baja_no_permitida", e.getMessage());
        }
    }

    private static Response sinCuerpo() {
        return error(Response.Status.BAD_REQUEST, "datos_invalidos", "El cuerpo es obligatorio");
    }

    private static Response error(Response.Status estado, String codigo, String detalle) {
        return Response.status(estado)
                .entity(Map.of("error", codigo, "detalle", detalle))
                .build();
    }
}

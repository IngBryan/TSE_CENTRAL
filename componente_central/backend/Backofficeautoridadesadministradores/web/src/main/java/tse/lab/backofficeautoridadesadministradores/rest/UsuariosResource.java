package tse.lab.backofficeautoridadesadministradores.rest;

import java.util.Map;

import jakarta.ejb.EJB;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.AltaUsuarioService;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.AltaUsuarioService.UsuarioCreado;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.DatosUsuarioInvalidosException;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.GestionUsuariosService;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.PaginaInvalidaException;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.UsuarioExistenteException;

/**
 * API REST de los usuarios del sistema (listado y alta), solo para
 * administradores.
 */
@Path("/usuarios")
@Produces(MediaType.APPLICATION_JSON)
public class UsuariosResource {

    @EJB
    private GestionUsuariosService servicio;

    @EJB
    private AltaUsuarioService alta;

    @Context
    private SecurityContext seguridad;

    // contrasena: administrador y autoridad; email: funcionario
    public record AltaUsuarioRequest(
            String tipo,
            String ci,
            String contrasena,
            String email) {
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

        Response acceso = AccesoAdministrador.verificar(seguridad);

        if (acceso != null) {
            return acceso;
        }

        try {
            return Response.ok(servicio.listar(pagina, tamanio, rol, ci)).build();

        } catch (PaginaInvalidaException e) {
            return error(Response.Status.BAD_REQUEST, "datos_invalidos", e.getMessage());
        }
    }

    /**
     * Da de alta un administrador, una autoridad o un funcionario.
     * Responde 201, 400 si los datos no son válidos o 409 si la cédula ya
     * tiene ese usuario.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response crear(AltaUsuarioRequest datos) {
        Response acceso = AccesoAdministrador.verificar(seguridad);

        if (acceso != null) {
            return acceso;
        }

        if (datos == null) {
            return error(Response.Status.BAD_REQUEST, "datos_invalidos", "El cuerpo es obligatorio");
        }

        try {
            UsuarioCreado creado = alta.crear(
                    seguridad.getUserPrincipal().getName(),
                    datos.tipo(),
                    datos.ci(),
                    datos.contrasena(),
                    datos.email());

            return Response.status(Response.Status.CREATED).entity(creado).build();

        } catch (DatosUsuarioInvalidosException e) {
            return error(Response.Status.BAD_REQUEST, "datos_invalidos", e.getMessage());

        } catch (UsuarioExistenteException e) {
            return error(Response.Status.CONFLICT, "usuario_existente", e.getMessage());
        }
    }

    private static Response error(Response.Status estado, String codigo, String detalle) {
        return Response.status(estado)
                .entity(Map.of("error", codigo, "detalle", detalle))
                .build();
    }
}

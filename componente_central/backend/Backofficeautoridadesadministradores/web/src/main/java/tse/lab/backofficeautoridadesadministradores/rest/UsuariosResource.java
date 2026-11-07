package tse.lab.backofficeautoridadesadministradores.rest;

import java.util.Map;

import jakarta.ejb.EJB;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.GestionUsuariosService;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.PaginaInvalidaException;

/**
 * API REST de los usuarios del sistema, solo para administradores.
 */
@Path("/usuarios")
@Produces(MediaType.APPLICATION_JSON)
public class UsuariosResource {

    @EJB
    private GestionUsuariosService servicio;

    @Context
    private SecurityContext seguridad;

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
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                            "error", "datos_invalidos",
                            "detalle", e.getMessage()))
                    .build();
        }
    }
}

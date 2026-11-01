package tse.lab.backofficeautoridadesadministradores.rest;

import java.util.Map;

import jakarta.ejb.EJB;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import tse.lab.backofficeautoridadesadministradores.service.gestiontipospermiso.GestionTiposPermisoService;
import tse.lab.backofficeautoridadesadministradores.service.gestiontipospermiso.TipoPermisoDuplicadoException;
import tse.lab.backofficeautoridadesadministradores.service.gestiontipospermiso.TipoPermisoInvalidoException;
import tse.lab.backofficeautoridadesadministradores.service.gestiontipospermiso.TipoPermisoNoEncontradoException;

/**
 * API REST del catálogo de tipos de permiso a demanda.
 * Solo la usa el rol ADMINISTRADOR.
 */
@Path("/tipos-permiso")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TiposPermisoResource {

    @EJB
    private GestionTiposPermisoService servicio;

    @Context
    private SecurityContext seguridad;

    public record TipoPermisoRequest(
            String nombre,
            boolean pideNumero,
            boolean pideVencimiento,
            boolean activo) {
    }

    interface Operacion {
        Response ejecutar();
    }

    @GET
    public Response listar() {
        return ejecutar(() -> Response.ok(servicio.listarTodos()).build());
    }

    @POST
    public Response registrar(TipoPermisoRequest datos) {
        return ejecutar(() -> datos == null
                ? cuerpoObligatorio()
                : Response.status(Response.Status.CREATED)
                        .entity(servicio.registrar(
                                datos.nombre(),
                                datos.pideNumero(),
                                datos.pideVencimiento()))
                        .build());
    }

    @PUT
    @Path("/{id}")
    public Response modificar(@PathParam("id") Long id, TipoPermisoRequest datos) {
        return ejecutar(() -> datos == null
                ? cuerpoObligatorio()
                : Response.ok(servicio.modificar(
                                id,
                                datos.nombre(),
                                datos.pideNumero(),
                                datos.pideVencimiento(),
                                datos.activo()))
                        .build());
    }

    private Response ejecutar(Operacion operacion) {
        if (seguridad == null || seguridad.getUserPrincipal() == null) {
            return error(Response.Status.UNAUTHORIZED, "no_autenticado", null);
        }

        if (!seguridad.isUserInRole("ADMINISTRADOR")) {
            return error(Response.Status.FORBIDDEN, "no_autorizado", null);
        }

        try {
            return operacion.ejecutar();
        } catch (TipoPermisoInvalidoException e) {
            return error(Response.Status.BAD_REQUEST, "datos_invalidos", e.getMessage());
        } catch (TipoPermisoDuplicadoException e) {
            return error(Response.Status.CONFLICT, "tipo_duplicado", e.getMessage());
        } catch (TipoPermisoNoEncontradoException e) {
            return error(Response.Status.NOT_FOUND, "tipo_no_encontrado", e.getMessage());
        }
    }

    private static Response cuerpoObligatorio() {
        return error(Response.Status.BAD_REQUEST, "datos_invalidos", "El cuerpo es obligatorio");
    }

    private static Response error(Response.Status status, String codigo, String detalle) {
        return Response.status(status)
                .entity(detalle == null
                        ? Map.of("error", codigo)
                        : Map.of("error", codigo, "detalle", detalle))
                .build();
    }
}

package tse.lab.backofficeautoridadesadministradores.rest;

import java.util.Map;
import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

import tse.lab.backofficeautoridadesadministradores.rest.seguridad.AutenticacionHttpMechanism;

/**
 * Mientras el usuario no cambie la contraseña inicial que le definió un
 * administrador, la API solo le deja ver quién es, cambiarla y cerrar la
 * sesión: todo lo demás responde 403 cambio_contrasena_requerido. Cubre
 * cualquier endpoint, también los que se agreguen después.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class CambioContrasenaPendienteFilter implements ContainerRequestFilter {

    // Rutas relativas a /api, sin barras al principio ni al final
    static final Set<String> PERMITIDAS = Set.of("auth/me", "auth/contrasena", "auth/logout", "auth/login");

    @Override
    public void filter(ContainerRequestContext req) {
        SecurityContext seguridad = req.getSecurityContext();

        if (seguridad == null
                || seguridad.getUserPrincipal() == null
                || !seguridad.isUserInRole(AutenticacionHttpMechanism.CAMBIO_CONTRASENA_PENDIENTE)
                || PERMITIDAS.contains(normalizar(req.getUriInfo().getPath()))) {
            return;
        }

        req.abortWith(Response.status(Response.Status.FORBIDDEN)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Map.of(
                        "error", "cambio_contrasena_requerido",
                        "detalle", "Tenés que cambiar tu contraseña antes de continuar"))
                .build());
    }

    private static String normalizar(String ruta) {
        String sinInicio = ruta == null ? "" : ruta.replaceAll("^/+", "");
        return sinInicio.replaceAll("/+$", "");
    }
}

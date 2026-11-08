package tse.lab.backofficeautoridadesadministradores.rest;

import java.util.Map;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Control de acceso de las operaciones exclusivas del rol ADMINISTRADOR.
 */
final class AccesoAdministrador {

    private AccesoAdministrador() {
    }

    static Response verificar(SecurityContext seguridad) {
        if (seguridad == null
                || seguridad.getUserPrincipal() == null) {

            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "no_autenticado"))
                    .build();
        }

        if (!seguridad.isUserInRole("ADMINISTRADOR")) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(Map.of("error", "no_autorizado"))
                    .build();
        }

        return null;
    }
}

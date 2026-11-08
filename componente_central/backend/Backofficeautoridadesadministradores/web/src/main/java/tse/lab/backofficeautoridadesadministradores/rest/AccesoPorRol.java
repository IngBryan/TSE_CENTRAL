package tse.lab.backofficeautoridadesadministradores.rest;

import java.util.Map;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

/**
 * Centraliza la validación de acceso por roles permitidos.
 */
final class AccesoPorRol {

    private AccesoPorRol() {
    }

    static Response verificar(SecurityContext seguridad, String... rolesPermitidos) {
        if (seguridad == null || seguridad.getUserPrincipal() == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "no_autenticado"))
                    .build();
        }

        for (String rol : rolesPermitidos) {
            if (seguridad.isUserInRole(rol)) {
                return null;
            }
        }

        return Response.status(Response.Status.FORBIDDEN)
                .entity(Map.of("error", "no_autorizado"))
                .build();
    }
}

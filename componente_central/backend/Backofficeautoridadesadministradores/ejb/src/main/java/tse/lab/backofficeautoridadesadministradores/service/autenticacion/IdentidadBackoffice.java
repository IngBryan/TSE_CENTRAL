
package tse.lab.backofficeautoridadesadministradores.service.autenticacion;

/**
 * Identidad de un usuario autenticado del Backoffice.
 *
 * No contiene contraseñas ni hashes.
 */
public record IdentidadBackoffice(
        String ci,
        String rol) {
}

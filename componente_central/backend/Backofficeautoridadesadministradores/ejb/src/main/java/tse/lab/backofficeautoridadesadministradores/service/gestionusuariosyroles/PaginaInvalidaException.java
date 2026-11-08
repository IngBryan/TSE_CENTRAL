package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import jakarta.ejb.ApplicationException;

/**
 * Los parámetros del listado de usuarios (página, tamaño, rol o cédula)
 * no son válidos.
 */
@ApplicationException(rollback = true)
public class PaginaInvalidaException extends RuntimeException {

    public PaginaInvalidaException(String mensaje) {
        super(mensaje);
    }
}

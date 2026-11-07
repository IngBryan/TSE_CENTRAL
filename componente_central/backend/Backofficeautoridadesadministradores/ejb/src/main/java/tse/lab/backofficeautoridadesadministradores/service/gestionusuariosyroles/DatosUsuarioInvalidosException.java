package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import jakarta.ejb.ApplicationException;

/**
 * Los datos para dar de alta un usuario no son válidos.
 */
@ApplicationException(rollback = true)
public class DatosUsuarioInvalidosException extends RuntimeException {

    public DatosUsuarioInvalidosException(String mensaje) {
        super(mensaje);
    }
}

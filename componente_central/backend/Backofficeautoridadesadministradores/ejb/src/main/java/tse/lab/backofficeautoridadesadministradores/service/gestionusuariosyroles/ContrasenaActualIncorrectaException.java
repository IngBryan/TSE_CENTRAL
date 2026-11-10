package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import jakarta.ejb.ApplicationException;

/**
 * La contraseña actual que dio el usuario para cambiarla no es la suya.
 */
@ApplicationException(rollback = true)
public class ContrasenaActualIncorrectaException extends RuntimeException {

    public ContrasenaActualIncorrectaException(String mensaje) {
        super(mensaje);
    }
}

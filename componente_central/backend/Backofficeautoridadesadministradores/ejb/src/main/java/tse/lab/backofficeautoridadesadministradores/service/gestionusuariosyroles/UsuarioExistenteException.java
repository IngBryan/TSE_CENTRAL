package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import jakarta.ejb.ApplicationException;

/**
 * La cédula ya tiene un usuario del tipo que se quiere dar de alta.
 */
@ApplicationException(rollback = true)
public class UsuarioExistenteException extends RuntimeException {

    public UsuarioExistenteException(String mensaje) {
        super(mensaje);
    }
}

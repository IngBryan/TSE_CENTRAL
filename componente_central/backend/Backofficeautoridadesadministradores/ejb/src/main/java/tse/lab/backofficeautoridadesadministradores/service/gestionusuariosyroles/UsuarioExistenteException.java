package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import jakarta.ejb.ApplicationException;

/**
 * La cédula ya es funcionario, administrador o autoridad: una persona tiene
 * un solo rol interno.
 */
@ApplicationException(rollback = true)
public class UsuarioExistenteException extends RuntimeException {

    public UsuarioExistenteException(String mensaje) {
        super(mensaje);
    }
}

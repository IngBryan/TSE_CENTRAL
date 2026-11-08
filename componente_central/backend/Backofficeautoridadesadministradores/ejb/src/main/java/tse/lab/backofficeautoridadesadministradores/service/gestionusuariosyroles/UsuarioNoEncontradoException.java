package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import jakarta.ejb.ApplicationException;

/**
 * La cédula no es funcionario, administrador ni autoridad.
 */
@ApplicationException(rollback = true)
public class UsuarioNoEncontradoException extends RuntimeException {

    public UsuarioNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}

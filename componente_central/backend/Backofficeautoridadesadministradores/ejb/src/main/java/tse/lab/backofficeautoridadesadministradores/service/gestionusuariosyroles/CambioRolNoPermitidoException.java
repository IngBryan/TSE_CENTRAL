package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import jakarta.ejb.ApplicationException;

/**
 * El cambio de rol no se puede hacer: la persona ya tiene ese rol, o el
 * administrador intenta cambiar el suyo.
 */
@ApplicationException(rollback = true)
public class CambioRolNoPermitidoException extends RuntimeException {

    public CambioRolNoPermitidoException(String mensaje) {
        super(mensaje);
    }
}

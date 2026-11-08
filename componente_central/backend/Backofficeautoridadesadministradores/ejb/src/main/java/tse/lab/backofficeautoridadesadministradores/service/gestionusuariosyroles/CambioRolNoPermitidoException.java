package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import jakarta.ejb.ApplicationException;

/**
 * El cambio de rol no se puede hacer: la persona ya tiene ese rol, el
 * administrador intenta cambiar el suyo, o quien lo hace ya no es
 * administrador.
 */
@ApplicationException(rollback = true)
public class CambioRolNoPermitidoException extends RuntimeException {

    public CambioRolNoPermitidoException(String mensaje) {
        super(mensaje);
    }
}

package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import jakarta.ejb.ApplicationException;

/**
 * La baja no se puede hacer: el administrador intenta eliminarse a sí mismo,
 * o quien la hace ya no es administrador.
 */
@ApplicationException(rollback = true)
public class BajaNoPermitidaException extends RuntimeException {

    public BajaNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}

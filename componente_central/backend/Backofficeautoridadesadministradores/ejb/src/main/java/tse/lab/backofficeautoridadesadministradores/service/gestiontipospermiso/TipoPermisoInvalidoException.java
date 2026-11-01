package tse.lab.backofficeautoridadesadministradores.service.gestiontipospermiso;

import jakarta.ejb.ApplicationException;

/**
 * Los datos del tipo de permiso no son válidos.
 */
@ApplicationException(rollback = true)
public class TipoPermisoInvalidoException extends RuntimeException {

    public TipoPermisoInvalidoException(String mensaje) {
        super(mensaje);
    }
}

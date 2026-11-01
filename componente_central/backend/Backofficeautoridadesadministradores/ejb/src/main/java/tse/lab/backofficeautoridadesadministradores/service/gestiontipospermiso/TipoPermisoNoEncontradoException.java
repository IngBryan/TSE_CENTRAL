package tse.lab.backofficeautoridadesadministradores.service.gestiontipospermiso;

import jakarta.ejb.ApplicationException;

/**
 * El tipo de permiso no existe.
 */
@ApplicationException(rollback = true)
public class TipoPermisoNoEncontradoException extends RuntimeException {

    public TipoPermisoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}

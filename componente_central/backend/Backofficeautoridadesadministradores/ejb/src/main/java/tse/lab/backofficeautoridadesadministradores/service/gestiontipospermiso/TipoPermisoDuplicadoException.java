package tse.lab.backofficeautoridadesadministradores.service.gestiontipospermiso;

import jakarta.ejb.ApplicationException;

/**
 * Ya existe otro tipo de permiso con ese nombre.
 */
@ApplicationException(rollback = true)
public class TipoPermisoDuplicadoException extends RuntimeException {

    public TipoPermisoDuplicadoException(String mensaje) {
        super(mensaje);
    }
}

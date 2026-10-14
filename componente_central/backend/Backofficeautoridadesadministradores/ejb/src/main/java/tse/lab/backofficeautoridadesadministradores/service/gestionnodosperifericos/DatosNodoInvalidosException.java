
package tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos;

import jakarta.ejb.ApplicationException;

/**
 * Representa un error de validación de los datos
 * proporcionados para registrar un nodo periférico.
 */
@ApplicationException(rollback = true)
public class DatosNodoInvalidosException extends RuntimeException {

    public DatosNodoInvalidosException(String mensaje) {
        super(mensaje);
    }
}

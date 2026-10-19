
package tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos;

import jakarta.ejb.ApplicationException;

/**
 * Indica que ya existe un nodo periférico del mismo tipo
 * con el identificador externo solicitado.
 */
@ApplicationException(rollback = true)
public class NodoDuplicadoException extends RuntimeException {

    public NodoDuplicadoException(
            TipoNodo tipo,
            Long idExterno) {

        super("Ya existe un nodo de tipo " + tipo
                + " con identificador externo " + idExterno);
    }
}


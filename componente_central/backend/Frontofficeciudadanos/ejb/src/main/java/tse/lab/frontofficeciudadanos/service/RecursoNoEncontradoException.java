package tse.lab.frontofficeciudadanos.service;

import jakarta.ejb.ApplicationException;

// El recurso pedido no existe o no es de la empresa de quien llama. La REST la traduce a 404.
@ApplicationException(rollback = true)
public class RecursoNoEncontradoException extends Exception {
    private static final long serialVersionUID = 1L;

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}

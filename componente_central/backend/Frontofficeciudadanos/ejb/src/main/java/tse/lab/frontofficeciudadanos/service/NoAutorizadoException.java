package tse.lab.frontofficeciudadanos.service;

import jakarta.ejb.ApplicationException;

// Quien llama no puede hacer la operacion (por ejemplo, no es responsable validado de una empresa
// habilitada). La REST la traduce a 403.
@ApplicationException(rollback = true)
public class NoAutorizadoException extends Exception {
    private static final long serialVersionUID = 1L;

    public NoAutorizadoException(String motivo) {
        super(motivo);
    }
}

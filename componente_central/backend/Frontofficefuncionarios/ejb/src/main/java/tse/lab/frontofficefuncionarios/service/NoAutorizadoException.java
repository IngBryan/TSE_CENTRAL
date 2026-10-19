package tse.lab.frontofficefuncionarios.service;

import jakarta.ejb.ApplicationException;

// Quien llama no es un funcionario autorizado para la operacion. La REST la traduce a 403.
@ApplicationException(rollback = true)
public class NoAutorizadoException extends Exception {
    private static final long serialVersionUID = 1L;

    public NoAutorizadoException(String motivo) {
        super(motivo);
    }
}

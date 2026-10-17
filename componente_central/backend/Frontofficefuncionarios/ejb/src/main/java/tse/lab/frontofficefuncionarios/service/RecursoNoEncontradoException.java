package tse.lab.frontofficefuncionarios.service;

import jakarta.ejb.ApplicationException;

// Lo pedido no existe (CASO_NO_ENCONTRADO, SIN_DESCARGO...). La REST la traduce a 404 con el codigo.
@ApplicationException(rollback = true)
public class RecursoNoEncontradoException extends Exception {
    private static final long serialVersionUID = 1L;

    private final String codigo;

    public RecursoNoEncontradoException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}

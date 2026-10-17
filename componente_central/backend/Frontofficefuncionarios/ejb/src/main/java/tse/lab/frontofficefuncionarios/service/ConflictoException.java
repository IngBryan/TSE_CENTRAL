package tse.lab.frontofficefuncionarios.service;

import jakarta.ejb.ApplicationException;

// La operacion no se puede hacer por el estado actual (por ejemplo, resolver un caso que no esta en
// descargo). La REST la traduce a 409 con el codigo.
@ApplicationException(rollback = true)
public class ConflictoException extends Exception {
    private static final long serialVersionUID = 1L;

    private final String codigo;

    public ConflictoException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}

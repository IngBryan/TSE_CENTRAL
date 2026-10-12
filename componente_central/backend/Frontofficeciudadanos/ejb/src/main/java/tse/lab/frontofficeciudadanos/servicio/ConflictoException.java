package tse.lab.frontofficeciudadanos.servicio;

import jakarta.ejb.ApplicationException;

// La operacion no se puede hacer por el estado actual (por ejemplo, la guia ya esta asignada).
// La REST la traduce a 409.
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

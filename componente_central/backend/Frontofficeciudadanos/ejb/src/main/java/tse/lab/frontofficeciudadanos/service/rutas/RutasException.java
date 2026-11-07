package tse.lab.frontofficeciudadanos.service.rutas;

import jakarta.ejb.ApplicationException;

/** Error controlado de la integracion cartografica. */
@ApplicationException(rollback = true)
public class RutasException extends RuntimeException {
    private final int status;
    private final String codigo;

    public RutasException(int status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }

    public RutasException(int status, String codigo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.status = status;
        this.codigo = codigo;
    }

    public int status() { return status; }
    public String codigo() { return codigo; }
}

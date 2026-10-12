package tse.lab.frontofficeciudadanos.pdi;

// La PDI no respondio, respondio con un SOAP Fault o con algo que no se pudo interpretar.
public class PdiNoDisponibleException extends Exception {
    private static final long serialVersionUID = 1L;

    public PdiNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public PdiNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}

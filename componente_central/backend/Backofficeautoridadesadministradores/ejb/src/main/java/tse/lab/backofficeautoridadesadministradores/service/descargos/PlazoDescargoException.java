package tse.lab.backofficeautoridadesadministradores.service.descargos;

import jakarta.ejb.ApplicationException;

@ApplicationException(rollback = true)
public class PlazoDescargoException extends RuntimeException {
    private final int status;
    public PlazoDescargoException(int status, String mensaje) { super(mensaje); this.status = status; }
    public int getStatus() { return status; }
}

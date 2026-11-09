package tse.lab.backofficeautoridadesadministradores.service.reglas;

import jakarta.ejb.ApplicationException;

@ApplicationException(rollback=true)
public class ReglasException extends RuntimeException {
    private final int status;
    public ReglasException(int status, String message) { super(message); this.status=status; }
    public int getStatus() { return status; }
}

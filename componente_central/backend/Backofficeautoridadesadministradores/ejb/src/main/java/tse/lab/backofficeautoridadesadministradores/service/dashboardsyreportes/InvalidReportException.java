package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes;

import jakarta.ejb.ApplicationException;

// A report that cannot be calculated as asked: an unknown KPI, a half day, a grouping a KPI is not linked to
@ApplicationException
public class InvalidReportException extends RuntimeException {

    public InvalidReportException(String message) {
        super(message);
    }
}

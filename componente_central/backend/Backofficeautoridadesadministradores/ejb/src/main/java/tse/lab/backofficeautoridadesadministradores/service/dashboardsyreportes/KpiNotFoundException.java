package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes;

import jakarta.ejb.ApplicationException;

@ApplicationException
public class KpiNotFoundException extends RuntimeException {

    public KpiNotFoundException(String id) {
        super("Unknown KPI: " + id);
    }
}

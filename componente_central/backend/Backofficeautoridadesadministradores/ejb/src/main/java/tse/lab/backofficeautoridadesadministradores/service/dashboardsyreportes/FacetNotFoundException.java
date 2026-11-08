package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes;

import jakarta.ejb.ApplicationException;

@ApplicationException
public class FacetNotFoundException extends RuntimeException {

    public FacetNotFoundException(String id) {
        super("Unknown facet: " + id);
    }
}

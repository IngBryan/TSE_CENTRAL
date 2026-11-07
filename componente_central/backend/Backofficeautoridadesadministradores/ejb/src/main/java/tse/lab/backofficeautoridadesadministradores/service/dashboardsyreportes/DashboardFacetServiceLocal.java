package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes;

import java.util.List;

import jakarta.ejb.Local;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.FacetValue;

@Local
public interface DashboardFacetServiceLocal {

    List<FacetDefinition> facets();

    List<FacetValue> values(String facetId);

    record FacetDefinition(String id, String name, String kind) {
    }
}


package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.CargoTypeFacet;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.CategoryFacet;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.DepartmentFacet;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.Facet;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.FacetValue;

@Stateless
public class DashboardFacetService implements DashboardFacetServiceLocal {

    @PersistenceContext(unitName = "backofficeautoridadesadministradoresPersistenceUnit")
    private EntityManager em;

    @Override
    public List<FacetDefinition> facets() {
        return List.of(
                new FacetDefinition("department", "Departamento", "catalog"),
                new FacetDefinition("cargoType", "Tipo de carga", "catalog"),
                new FacetDefinition("category", "Rubro", "catalog"),
                new FacetDefinition("time", "Tiempo", "time"));
    }

    @Override
    public List<FacetValue> values(String facetId) {
        Facet facet = resolve(facetId);
        if (facetId.equals("time")) {
            throw new IllegalArgumentException("La faceta time no tiene valores fijos; se calcula en el front usando el período seleccionado");
        }
        return facet.values(em);
    }

    private Facet resolve(String facetId) {
        return switch (facetId) {
            case "department" -> new DepartmentFacet();
            case "cargoType" -> new CargoTypeFacet();
            case "category" -> new CategoryFacet();
            case "time" -> throw new IllegalArgumentException("La faceta time no es un catálogo: se resuelve en el front");
            default -> throw new IllegalArgumentException("Facet no soportada: " + facetId);
        };
    }
}

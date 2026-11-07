package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Table;

/**
 * The department a trip left from: the one of its origin locality. A case is in the one of its trip. Departments
 * have no table, so a department is its own id. Companies and vehicles keep no address: they are not linked.
 */
public class DepartmentFacet extends Facet {

    public DepartmentFacet() {
        super("department", Map.of(
                Table.TRIP, new Link(
                        List.of("LEFT JOIN {t}.origenLocalidad origin"),
                        "origin.departamento", "origin.departamento"),
                Table.CASE, new Link(
                        List.of("LEFT JOIN {t}.guia caseTrip", "LEFT JOIN caseTrip.origenLocalidad origin"),
                        "origin.departamento", "origin.departamento")));
    }

    @Override
    public List<FacetValue> values(EntityManager em) {
        return em.createQuery("SELECT DISTINCT l.departamento FROM Localidad l ORDER BY l.departamento", String.class)
                .getResultList().stream()
                .map(department -> new FacetValue(department, department))
                .toList();
    }
}

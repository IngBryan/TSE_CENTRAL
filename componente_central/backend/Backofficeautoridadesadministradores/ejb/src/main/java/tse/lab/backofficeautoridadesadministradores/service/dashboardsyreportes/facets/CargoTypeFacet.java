package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Table;

/**
 * The cargo type of a trip, from the TipoCarga catalog; a case has the one of its trip. Trips keep the type's name,
 * so they join the catalog by name and group and filter by its id.
 */
public class CargoTypeFacet extends Facet {

    public CargoTypeFacet() {
        super("cargoType", Map.of(
                Table.TRIP, new Link(
                        List.of("LEFT JOIN TipoCarga cargoType ON cargoType.nombre = {t}.tipoCarga"),
                        "cargoType.id", "cargoType.nombre"),
                Table.CASE, new Link(
                        List.of("LEFT JOIN {t}.guia caseTrip",
                                "LEFT JOIN TipoCarga cargoType ON cargoType.nombre = caseTrip.tipoCarga"),
                        "cargoType.id", "cargoType.nombre")));
    }

    @Override
    public List<FacetValue> values(EntityManager em) {
        return em.createQuery("SELECT t.id, t.nombre FROM TipoCarga t ORDER BY t.nombre", Object[].class)
                .getResultList().stream()
                .map(row -> new FacetValue(row[0].toString(), (String) row[1]))
                .toList();
    }

    @Override
    protected Object parameterValues(List<String> ids) {
        return ids.stream().map(Long::valueOf).toList();
    }
}

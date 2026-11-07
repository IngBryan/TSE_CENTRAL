package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Table;

/**
 * The rubro, from the Rubro catalog: the client's one a trip keeps (by name, so it joins the catalog by name), the
 * one of its trip for a case, the ones its company works in for a company or vehicle (a company with two rubros
 * is in both groups).
 */
public class CategoryFacet extends Facet {

    public CategoryFacet() {
        super("category", Map.of(
                Table.COMPANY, new Link(
                        List.of("LEFT JOIN {t}.rubros category"),
                        "category.id", "category.nombre"),
                Table.VEHICLE, new Link(
                        List.of("LEFT JOIN {t}.empresa vehicleCompany", "LEFT JOIN vehicleCompany.rubros category"),
                        "category.id", "category.nombre"),
                Table.TRIP, new Link(
                        List.of("LEFT JOIN Rubro category ON category.nombre = {t}.rubroCliente"),
                        "category.id", "category.nombre"),
                Table.CASE, new Link(
                        List.of("LEFT JOIN {t}.guia caseTrip",
                                "LEFT JOIN Rubro category ON category.nombre = caseTrip.rubroCliente"),
                        "category.id", "category.nombre")));
    }

    @Override
    public List<FacetValue> values(EntityManager em) {
        return em.createQuery("SELECT r.id, r.nombre FROM Rubro r ORDER BY r.nombre", Object[].class)
                .getResultList().stream()
                .map(row -> new FacetValue(row[0].toString(), (String) row[1]))
                .toList();
    }

    @Override
    protected Object parameterValues(List<String> ids) {
        return ids.stream().map(Long::valueOf).toList();
    }
}

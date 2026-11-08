package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Table;

/**
 * The company, by its number, shown by its public name (its legal name when it has none): the one a trip is made
 * for, the one that owns the vehicle of a case (a case without a trip still has a vehicle), the one of a vehicle,
 * and a company itself.
 */
public class CompanyFacet extends Facet {

    private static final String VALUES = "SELECT e.nroEmpresa, COALESCE(e.nombrePublico, e.razonSocial) FROM Empresa e "
            + "ORDER BY COALESCE(e.nombrePublico, e.razonSocial)";

    public CompanyFacet() {
        super("company", "Empresa", Map.of(
                Table.TRIP, link(List.of("LEFT JOIN {t}.empresa tripCompany"), "tripCompany"),
                Table.CASE, link(List.of("LEFT JOIN {t}.vehiculo caseVehicle",
                        "LEFT JOIN caseVehicle.empresa caseCompany"), "caseCompany"),
                Table.VEHICLE, link(List.of("LEFT JOIN {t}.empresa vehicleCompany"), "vehicleCompany"),
                Table.COMPANY, link(List.of(), "{t}")));
    }

    private static Link link(List<String> joins, String company) {
        return new Link(joins, company + ".nroEmpresa", name(company));
    }

    private static String name(String company) {
        return "COALESCE(" + company + ".nombrePublico, " + company + ".razonSocial)";
    }

    @Override
    public List<FacetValue> values(EntityManager em) {
        return em.createQuery(VALUES, Object[].class)
                .getResultList().stream()
                .map(row -> new FacetValue(row[0].toString(), (String) row[1]))
                .toList();
    }

    @Override
    protected Object parameterValues(List<String> ids) {
        return ids.stream().map(Long::valueOf).toList();
    }
}

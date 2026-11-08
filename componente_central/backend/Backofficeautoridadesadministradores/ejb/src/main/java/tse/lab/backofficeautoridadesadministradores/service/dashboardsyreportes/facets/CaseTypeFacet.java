package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.persistence.EntityManager;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Table;

/**
 * The kind of infraction of a case: "EXCESO_CARGA", "VIAJE_SIN_GUIA". There is no catalog of them, so the values
 * are the kinds the cases have and the names are written here. Only cases have one.
 */
public class CaseTypeFacet extends Facet {

    private static final Map<String, String> NAMES = Map.of(
            "EXCESO_CARGA", "Exceso de carga",
            "VIAJE_SIN_GUIA", "Viaje sin guía");

    public CaseTypeFacet() {
        super("caseType", "Tipo de infracción", Map.of(Table.CASE, Link.of("{t}.tipoCaso")));
    }

    @Override
    public List<FacetValue> values(EntityManager em) {
        return em.createQuery("SELECT DISTINCT c.tipoCaso FROM CasoFiscalizacion c WHERE c.tipoCaso IS NOT NULL "
                        + "ORDER BY c.tipoCaso", String.class)
                .getResultList().stream()
                .map(code -> new FacetValue(code, name(code)))
                .toList();
    }

    @Override
    public FacetValue key(Link link, Object[] columns) {
        FacetValue value = super.key(link, columns);
        return value == null ? null : new FacetValue(value.id(), name(value.id()));
    }

    // A kind without a name of its own reads as its code: "DESVIO_RUTA" -> "Desvio ruta"
    private static String name(String code) {
        String name = NAMES.get(code);
        if (name != null) {
            return name;
        }
        String words = code.replace('_', ' ').toLowerCase(Locale.ROOT);
        return words.isEmpty() ? code : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}

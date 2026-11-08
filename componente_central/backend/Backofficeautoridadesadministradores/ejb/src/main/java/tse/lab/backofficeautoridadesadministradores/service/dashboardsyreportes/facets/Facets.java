package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets.TimeFacet.Granularity;

// The facets the reports group and filter by
public final class Facets {

    public static final TimeFacet DAY = new TimeFacet(Granularity.DAY);
    public static final TimeFacet WEEK = new TimeFacet(Granularity.WEEK);
    public static final TimeFacet MONTH = new TimeFacet(Granularity.MONTH);
    public static final TimeFacet YEAR = new TimeFacet(Granularity.YEAR);
    public static final DepartmentFacet DEPARTMENT = new DepartmentFacet();
    public static final CargoTypeFacet CARGO_TYPE = new CargoTypeFacet();
    public static final CategoryFacet CATEGORY = new CategoryFacet();

    // In the order a report offers them
    public static final List<Facet> ALL = List.of(DAY, WEEK, MONTH, YEAR, DEPARTMENT, CARGO_TYPE, CATEGORY);

    private static final Map<String, Facet> BY_ID = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(Facet::id, Function.identity()));

    private Facets() {
    }

    // The facet with that id, or null: "month", "department", "cargoType", "category"
    public static Facet byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }
}

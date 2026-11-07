package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.facets;

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

    private Facets() {
    }
}

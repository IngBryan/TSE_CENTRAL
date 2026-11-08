package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes;

import java.util.List;

// Values a filter includes and excludes at the same time: the included ones (all when none is) minus the excluded
public record Selection(List<String> include, List<String> exclude) {

    public static final Selection ALL = new Selection(List.of(), List.of());

    public Selection {
        include = include == null ? List.of() : List.copyOf(include);
        exclude = exclude == null ? List.of() : List.copyOf(exclude);
    }
}

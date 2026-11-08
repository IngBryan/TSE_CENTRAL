package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.composite.ConfirmationRateKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.CargoVolumeKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.ClosedCasesKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.ConfirmedInfractionsKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.DetectedInfractionsKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.EnabledCompaniesKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.EnabledVehiclesKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.TripsKpi;

// The KPIs a report can ask for, by id
public final class Kpis {

    // In the order a report offers them
    public static final List<Kpi> ALL = List.of(
            new TripsKpi(),
            new CargoVolumeKpi(),
            new DetectedInfractionsKpi(),
            new ConfirmedInfractionsKpi(),
            new ClosedCasesKpi(),
            new ConfirmationRateKpi(),
            new EnabledCompaniesKpi(),
            new EnabledVehiclesKpi());

    private static final Map<String, Kpi> BY_ID = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(Kpi::id, Function.identity()));

    private Kpis() {
    }

    // The KPI with that id, or null
    public static Kpi byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }
}

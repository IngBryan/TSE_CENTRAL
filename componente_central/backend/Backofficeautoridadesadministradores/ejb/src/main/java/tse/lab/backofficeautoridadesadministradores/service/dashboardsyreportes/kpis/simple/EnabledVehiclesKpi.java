package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple;

import java.util.List;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Table;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.TableAlias;

// Active vehicles of enabled companies with ITV and circulation permit in force on the day
public class EnabledVehiclesKpi extends SnapshotKpi {

    public EnabledVehiclesKpi() {
        super("vehiculos", "Vehículos habilitados");
    }

    @Override
    protected List<TableAlias> tables() {
        return List.of(TableAlias.main(Table.VEHICLE, "v"));
    }

    // Distinct: grouped or filtered by rubro, a vehicle comes once per rubro of its company
    @Override
    protected String measure() {
        return "COUNT(DISTINCT v)";
    }

    // In force on a day: issued on it or before, expiring on it or after
    @Override
    protected String condition(String day) {
        return "v.activo = TRUE AND v.empresa.habilitada = TRUE"
                + " AND EXISTS (SELECT inspection FROM ITV inspection WHERE inspection.vehiculo = v"
                + " AND inspection.fechaEmision <= " + day + " AND inspection.fechaVencimiento >= " + day + ")"
                + " AND EXISTS (SELECT permit FROM PermisoCirculacion permit WHERE permit.vehiculo = v"
                + " AND permit.fechaEmision <= " + day + " AND permit.fechaVencimiento >= " + day + ")";
    }
}

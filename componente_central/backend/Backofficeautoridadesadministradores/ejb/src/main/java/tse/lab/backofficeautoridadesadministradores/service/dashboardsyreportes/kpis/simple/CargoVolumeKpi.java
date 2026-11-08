package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple;

import java.util.List;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Table;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.TableAlias;

// Tons carried by the trips that ended in the period
public class CargoVolumeKpi extends BaseQueryKpi {

    public CargoVolumeKpi() {
        super("toneladas", "Volumen de carga transportado");
    }

    @Override
    protected List<TableAlias> tables() {
        return List.of(TableAlias.main(Table.TRIP, "g"));
    }

    @Override
    protected String measure() {
        return "SUM(g.pesoCargaKg) / 1000.0";
    }

    @Override
    protected String condition() {
        return "g.estado = tse.lab.model.EstadoGuia.FINALIZADA";
    }
}

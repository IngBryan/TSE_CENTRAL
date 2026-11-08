package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple;

import java.util.List;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Table;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.TableAlias;

// Trips that ended in the period
public class TripsKpi extends BaseQueryKpi {

    public TripsKpi() {
        super("viajes", "Viajes realizados");
    }

    @Override
    protected List<TableAlias> tables() {
        return List.of(TableAlias.main(Table.TRIP, "g"));
    }

    @Override
    protected String measure() {
        return "COUNT(g)";
    }

    @Override
    protected String condition() {
        return "g.estado = tse.lab.model.EstadoGuia.FINALIZADA";
    }
}

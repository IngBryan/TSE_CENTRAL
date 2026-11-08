package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple;

import java.util.List;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Table;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.TableAlias;

// Cases detected in the period and confirmed as infractions; a dismissed case is closed but not confirmed
public class ConfirmedInfractionsKpi extends BaseQueryKpi {

    public ConfirmedInfractionsKpi() {
        super("confirmadas", "Infracciones confirmadas");
    }

    @Override
    protected List<TableAlias> tables() {
        return List.of(TableAlias.main(Table.CASE, "c"));
    }

    @Override
    protected String measure() {
        return "COUNT(c)";
    }

    @Override
    protected String condition() {
        return "c.estado = tse.lab.model.EstadoCaso.CONFIRMADA";
    }
}

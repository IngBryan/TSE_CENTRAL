package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple;

import java.util.List;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Table;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.TableAlias;

// Cases detected in the period, whatever became of them
public class DetectedInfractionsKpi extends BaseQueryKpi {

    public DetectedInfractionsKpi() {
        super("infracciones", "Infracciones detectadas");
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
        return null;
    }
}

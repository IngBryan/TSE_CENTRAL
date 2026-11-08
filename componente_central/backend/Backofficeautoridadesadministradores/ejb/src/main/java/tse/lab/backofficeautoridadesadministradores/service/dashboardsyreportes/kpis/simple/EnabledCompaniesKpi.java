package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple;

import java.util.List;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.Table;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.TableAlias;

// Companies enabled on the day. The model keeps no date of the enablement, so for now every day gives today's
public class EnabledCompaniesKpi extends SnapshotKpi {

    public EnabledCompaniesKpi() {
        super("empresas", "Empresas habilitadas");
    }

    @Override
    protected List<TableAlias> tables() {
        return List.of(TableAlias.main(Table.COMPANY, "e"));
    }

    // Distinct: grouped or filtered by rubro, a company comes once per rubro it works in
    @Override
    protected String measure() {
        return "COUNT(DISTINCT e)";
    }

    @Override
    protected String condition(String day) {
        return "e.habilitada = TRUE";
    }
}

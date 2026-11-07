package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.composite;

import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.ConfirmedInfractionsKpi;
import tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes.kpis.simple.DetectedInfractionsKpi;

// Share of the cases detected in the period that are closed (confirmed or dismissed)
public class ConfirmationRateKpi extends CompositeKpi {

    public ConfirmationRateKpi() {
        super("tasa", "Tasa de casos cerrados sobre detectadas",
                new ConfirmedInfractionsKpi(), new DetectedInfractionsKpi());
    }
}

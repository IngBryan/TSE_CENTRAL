package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes;

import java.time.LocalDate;

// Both days included
public record Period(LocalDate from, LocalDate to) {

    public Period {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("A period needs a start that is not after its end");
        }
    }
}

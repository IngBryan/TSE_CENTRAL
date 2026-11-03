package tse.lab.backofficeautoridadesadministradores.service;

import tse.lab.log.LogEstructurado;

// Logs de Backoffice: fija el componente de LogEstructurado. Lo propio del modulo se agrega con campo().
public final class LogBackoffice {

    private static final String COMPONENTE = "backoffice";

    private LogBackoffice() {
    }

    public static LogEstructurado evento(String evento) {
        return LogEstructurado.de(COMPONENTE, evento);
    }
}

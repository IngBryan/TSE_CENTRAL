package tse.lab.fiscalizacionautomatica;

import java.util.logging.Level;

import tse.lab.log.LogEstructurado;

/** Logs of the automatic fiscalization: standard output for the normal flow, standard error for failures. */
public final class LogFiscalizacion {

    private static final String COMPONENTE = "fiscalizacionautomatica";

    private LogFiscalizacion() {
    }

    public static LogEstructurado evento(String evento) {
        return LogEstructurado.de(COMPONENTE, evento);
    }

    public static void info(LogEstructurado log) {
        System.out.println(log);
    }

    public static void error(LogEstructurado log) {
        System.err.println(log.nivel(Level.SEVERE));
    }
}

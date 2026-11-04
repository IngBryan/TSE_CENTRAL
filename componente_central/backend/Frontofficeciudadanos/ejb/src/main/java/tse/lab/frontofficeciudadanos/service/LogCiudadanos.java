package tse.lab.frontofficeciudadanos.service;

import java.util.logging.Level;

import tse.lab.log.LogEstructurado;

// Logs de Frontofficeciudadanos: fija el componente de LogEstructurado. Lo propio del modulo se agrega con campo().
public final class LogCiudadanos {

    private static final String COMPONENTE = "frontofficeciudadanos";

    private LogCiudadanos() {
    }

    public static LogEstructurado evento(String evento) {
        return LogEstructurado.de(COMPONENTE, evento);
    }

    public static LogEstructurado aviso(String evento) {
        return evento(evento).nivel(Level.WARNING);
    }
}

package tse.lab.frontofficefuncionarios.service;

import tse.lab.log.LogEstructurado;

// Logs de Frontofficefuncionarios: fija el componente de LogEstructurado. Lo propio del modulo se agrega con campo().
public final class LogFuncionarios {

    private static final String COMPONENTE = "frontofficefuncionarios";

    private LogFuncionarios() {
    }

    public static LogEstructurado evento(String evento) {
        return LogEstructurado.de(COMPONENTE, evento);
    }
}

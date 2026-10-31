package tse.lab.fiscalizacionautomatica.ruleengine.trips;

import java.util.Objects;

/**
 * Resultado explícito de una asociación temporal GPS–Guía.
 * SIN_GUIA significa que no hubo candidatas en la información consultada;
 * por sí solo NO constituye una infracción de viaje sin Guía.
 */
public record ResultadoAsociacionGpsGuia(Estado estado, String idGuia) {
    public enum Estado {
        ASOCIADO,
        SIN_GUIA,
        AMBIGUO,
        NO_DETERMINABLE
    }

    public ResultadoAsociacionGpsGuia {
        Objects.requireNonNull(estado, "estado");
        if (estado == Estado.ASOCIADO) {
            if (idGuia == null || idGuia.isBlank()) {
                throw new IllegalArgumentException("ASOCIADO requiere idGuia");
            }
        } else if (idGuia != null) {
            throw new IllegalArgumentException("Solo ASOCIADO puede tener idGuia");
        }
    }

    public static ResultadoAsociacionGpsGuia asociado(String id) {
        return new ResultadoAsociacionGpsGuia(Estado.ASOCIADO, id);
    }

    public static ResultadoAsociacionGpsGuia sinGuia() {
        return new ResultadoAsociacionGpsGuia(Estado.SIN_GUIA, null);
    }

    public static ResultadoAsociacionGpsGuia ambiguo() {
        return new ResultadoAsociacionGpsGuia(Estado.AMBIGUO, null);
    }

    public static ResultadoAsociacionGpsGuia noDeterminable() {
        return new ResultadoAsociacionGpsGuia(Estado.NO_DETERMINABLE, null);
    }

    public boolean correspondeA(String id) {
        return estado == Estado.ASOCIADO && idGuia.equals(id);
    }
}

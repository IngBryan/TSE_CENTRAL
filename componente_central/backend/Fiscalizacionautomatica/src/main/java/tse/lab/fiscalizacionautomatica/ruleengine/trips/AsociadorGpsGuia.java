package tse.lab.fiscalizacionautomatica.ruleengine.trips;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervaloGuiaGps;

/**
 * Asociación conservadora y recalculable por fecha de medición; no escribe
 * idGuia en MongoDB. Nunca convierte ambigüedad en una infracción.
 */
public final class AsociadorGpsGuia {
    private AsociadorGpsGuia() { }

    public static ResultadoAsociacionGpsGuia resolver(TrackingEvent gps, List<IntervaloGuiaGps> intervalos) {
        Objects.requireNonNull(gps, "gps");
        Objects.requireNonNull(intervalos, "intervalos");
        Instant instante = gps.timestamp();
        List<IntervaloGuiaGps> candidatas = intervalos.stream()
                .filter(i -> i.getMatricula().equals(gps.licensePlate())
                        && !instante.isBefore(i.getAsignacion())
                        && (i.getFinAplicado() == null || instante.isBefore(i.getFinAplicado())))
                .toList();

        // Si la Guía está FINALIZADA pero su FIN aplicado no existe, desconocemos
        // hasta cuándo cubrió esa matrícula. No podemos declarar SIN_GUIA ni
        // adjudicar arbitrariamente una Guía diferente en ese período incierto.
        if (candidatas.stream().anyMatch(i -> "FINALIZADA".equals(i.getEstado())
                && i.getFinAplicado() == null)) {
            return ResultadoAsociacionGpsGuia.noDeterminable();
        }

        // Un INICIO aplicado tiene prioridad sobre una mera asignación, pero
        // dos INICIOs compatibles no pueden desempatarse arbitrariamente.
        List<IntervaloGuiaGps> iniciadas = candidatas.stream()
                .filter(i -> i.getInicioAplicado() != null && !instante.isBefore(i.getInicioAplicado()))
                .toList();
        if (iniciadas.size() == 1) {
            return ResultadoAsociacionGpsGuia.asociado(iniciadas.getFirst().getIdGuia());
        }
        if (iniciadas.size() > 1) {
            return ResultadoAsociacionGpsGuia.ambiguo();
        }
        if (candidatas.size() == 1) {
            return ResultadoAsociacionGpsGuia.asociado(candidatas.getFirst().getIdGuia());
        }
        return candidatas.isEmpty() ? ResultadoAsociacionGpsGuia.sinGuia()
                : ResultadoAsociacionGpsGuia.ambiguo();
    }
}

package tse.lab.fiscalizacionautomatica.ruleengine.trips;

import java.io.IOException;
import java.util.Objects;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.AsociacionTemporalGuia;

/** Determina la Guía por fecha de medición, tanto para Tracking como para pesajes. */
public final class ContextoEventoSource {
    private final IntervalosGuiasGpsSource intervalos;

    public ContextoEventoSource(IntervalosGuiasGpsSource intervalos) {
        this.intervalos = Objects.requireNonNull(intervalos);
    }

    public ResultadoAsociacionGpsGuia resolver(VehicleEvent evento) throws IOException {
        // La consulta usa el mismo instante que el evento, no la hora de procesamiento.
        var datos = intervalos.buscar(evento.licensePlate(), evento.timestamp(), evento.timestamp().plusMillis(1));
        if (datos == null) {
            throw new IOException("No se recibieron intervalos de Guías");
        }
        var resultado = AsociacionTemporalGuia.resolver(evento.licensePlate(), evento.timestamp(), datos);
        return switch (resultado.estado()) {
            case ASOCIADO -> ResultadoAsociacionGpsGuia.asociado(resultado.idGuia());
            case SIN_GUIA -> ResultadoAsociacionGpsGuia.sinGuia();
            case AMBIGUO -> ResultadoAsociacionGpsGuia.ambiguo();
            case NO_DETERMINABLE -> ResultadoAsociacionGpsGuia.noDeterminable();
        };
    }
}

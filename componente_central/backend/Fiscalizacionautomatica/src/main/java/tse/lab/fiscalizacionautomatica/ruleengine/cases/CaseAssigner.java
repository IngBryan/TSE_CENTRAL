package tse.lab.fiscalizacionautomatica.ruleengine.cases;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import tse.lab.fiscalizacionautomatica.LogFiscalizacion;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.Detection;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.Situation;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.trips.ResultadoAsociacionGpsGuia;
import tse.lab.frontofficefuncionarios.service.gestioncasos.DatosCaso;

/** Asigna la evidencia a CADA infracción; las operaciones remotas son idempotentes por caso y evento. */
public class CaseAssigner implements Consumer<Detection> {
    private static final String DETECTADA = "DETECTADA";
    private final CaseStore cases;

    public CaseAssigner(CaseStore cases) {
        this.cases = cases;
    }

    @Override
    public void accept(Detection detection) {
        // Sin infracciones ni cambio de contexto de Guía, no hay ninguna operación remota.
        if (detection.situations().isEmpty() && !(detection.event() instanceof TrackingEvent
                && detection.contexto() != null
                && detection.contexto().estado() == ResultadoAsociacionGpsGuia.Estado.ASOCIADO)) return;
        try {
            assign(detection);
        } catch (IOException e) {
            // El worker deja el evento pendiente y reintenta. Lo ya registrado es idempotente.
            throw new UncheckedIOException(e);
        }
    }

    private void assign(Detection detection) throws IOException {
        ResultadoAsociacionGpsGuia contexto = detection.contexto();
        if (contexto == null || contexto.estado() == ResultadoAsociacionGpsGuia.Estado.AMBIGUO
                || contexto.estado() == ResultadoAsociacionGpsGuia.Estado.NO_DETERMINABLE) {
            throw new IOException("No se puede asignar un caso sin contexto temporal inequívoco");
        }
        String guia = contexto.estado() == ResultadoAsociacionGpsGuia.Estado.ASOCIADO
                ? contexto.idGuia() : null;
        if (guia != null && detection.event() instanceof TrackingEvent gps) {
            cases.cerrarCirculacionPorGuia(gps.licensePlate(), gps.timestamp());
        }
        for (Situation situacion : detection.situations()) {
            if (situacion == Situation.ROUTE_DEVIATION && guia == null) {
                throw new IOException("El desvío necesita una Guía asociada");
            }
            if (situacion == Situation.TRIP_WITHOUT_GUIDE && guia != null) {
                throw new IOException("Viaje sin Guía contradictorio con la asociación temporal");
            }
            DatosCaso caso;
            if (situacion == Situation.TRIP_WITHOUT_GUIDE) {
                if (!(detection.event() instanceof TrackingEvent gps)) continue;
                caso = cases.registrarCirculacionSinGuia(gps);
            } else if (situacion == Situation.ROUTE_DEVIATION) {
                if (!(detection.event() instanceof TrackingEvent gps) || detection.pruebaDesvio() == null) {
                    throw new IOException("La situación de desvío carece de prueba geométrica");
                }
                caso = cases.registrarDesvioConReglas(guia, gps, detection.pruebaDesvio(), detection.configuracion());
            } else if (situacion == Situation.VEHICLE_WITHOUT_VALID_PERMIT) {
                if (detection.facts() == null || detection.facts().vehicle() == null
                        || detection.facts().vehicle().getHabilitacion() == null) {
                    throw new IOException("La situación de habilitación carece de prueba documental");
                }
                caso = cases.registrarHabilitacion(guia, detection.event(),
                        detection.facts().vehicle().getHabilitacion());
            } else {
                long pesoBase = (long) detection.facts().vehicle().getPesoKg()
                        + detection.facts().vehicle().getCapacidadCargaKg();
                caso = cases.registrarConReglas(situacion.caseType(), guia, detection.event(), detection.configuracion(), pesoBase);
            }
            if (caso != null) log("caso.registrado", caso, detection.event());
            if (caso != null && DETECTADA.equals(caso.getEstado())) {
                cases.notify(caso.getId(), detection.event().correlationId());
                log("caso.notificado", caso, detection.event());
            }
        }
    }

    private static void log(String evento, DatosCaso caso, VehicleEvent event) {
        LogFiscalizacion.info(LogFiscalizacion.evento(evento).correlationId(event.correlationId())
                .campo("caso", caso.getId()).campo("tipo", caso.getTipoCaso())
                .campo("matricula", event.licensePlate()).campo("fechaHora", event.timestamp()));
    }
}

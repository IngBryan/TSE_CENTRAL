package tse.lab.fiscalizacionautomatica.ruleengine.cases;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.Detection;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.Situation;
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
        if (detection.situations().isEmpty()) {
            return;
        }
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
        for (Situation situacion : detection.situations()) {
            if (situacion == Situation.ROUTE_DEVIATION && guia == null) {
                throw new IOException("El desvío necesita una Guía asociada");
            }
            if (situacion == Situation.TRIP_WITHOUT_GUIDE && guia != null) {
                throw new IOException("Viaje sin Guía contradictorio con la asociación temporal");
            }
            DatosCaso caso = cases.registrar(situacion.caseType(), guia, detection.event());
            if (DETECTADA.equals(caso.getEstado())) {
                cases.notify(caso.getId());
            }
        }
    }
}

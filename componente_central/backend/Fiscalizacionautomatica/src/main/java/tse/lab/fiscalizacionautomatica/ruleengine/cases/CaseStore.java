package tse.lab.fiscalizacionautomatica.ruleengine.cases;

import java.io.IOException;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.RouteDeviationRule.PruebaDesvio;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.frontofficefuncionarios.service.gestioncasos.DatosCaso;
import tse.lab.frontofficefuncionarios.service.gestioncasos.TipoCasoAutomatico;
import java.time.Instant;
import tse.lab.model.EvidenciaHabilitacion;

/** Operación atómica e idempotente por (contexto, tipo, evento) en PostgreSQL. */
public interface CaseStore {
    default DatosCaso registrarHabilitacion(String idGuia, VehicleEvent evento,
            EvidenciaHabilitacion prueba) throws IOException {
        throw new IOException("El repositorio no implementa evidencias de habilitación");
    }

    /** Registra el evento en el caso abierto adecuado; lo crea si todavía no existe. */
    DatosCaso registrar(TipoCasoAutomatico tipo, String idGuia, VehicleEvent evento) throws IOException;

    /** Registra atómicamente caso, clave de GPS y prueba geométrica; nunca omitir la prueba. */
    default DatosCaso registrarDesvio(String idGuia, TrackingEvent evento, PruebaDesvio prueba)
            throws IOException {
        throw new IOException("El repositorio no implementa evidencias geométricas");
    }

    /** Puede devolver null si todavía falta confirmar movimiento con una segunda muestra GPS. */
    default DatosCaso registrarCirculacionSinGuia(TrackingEvent evento) throws IOException {
        throw new IOException("Repositorio sin seguimiento de circulaciones");
    }

    default void cerrarCirculacionPorGuia(String matricula, Instant fecha) throws IOException {
        // Implementaciones locales de pruebas pueden no gestionar circulaciones.
    }

    /** Notifies the case to the company: DETECTADA to NOTIFICADA. */
    DatosCaso notify(long caseId, String correlationId) throws IOException;
}

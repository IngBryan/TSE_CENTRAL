package tse.lab.fiscalizacionautomatica.ruleengine.cases;

import java.io.IOException;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.frontofficefuncionarios.service.gestioncasos.DatosCaso;
import tse.lab.frontofficefuncionarios.service.gestioncasos.TipoCasoAutomatico;

/** Operación atómica e idempotente por (contexto, tipo, evento) en PostgreSQL. */
public interface CaseStore {
    /** Registra el evento en el caso abierto adecuado; lo crea si todavía no existe. */
    DatosCaso registrar(TipoCasoAutomatico tipo, String idGuia, VehicleEvent evento) throws IOException;

    /** Notifica el caso a la empresa: DETECTADA -> NOTIFICADA. */
    DatosCaso notify(long caseId) throws IOException;
}

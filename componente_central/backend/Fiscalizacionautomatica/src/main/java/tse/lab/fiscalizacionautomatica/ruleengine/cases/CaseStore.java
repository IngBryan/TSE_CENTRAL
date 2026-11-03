package tse.lab.fiscalizacionautomatica.ruleengine.cases;

import java.io.IOException;
import java.util.List;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.frontofficefuncionarios.service.gestioncasos.DatosCaso;
import tse.lab.frontofficefuncionarios.service.gestioncasos.TipoCasoAutomatico;

/** Operations on the fiscalization cases (see {@link RemoteCaseStore}). They don't decide anything. */
public interface CaseStore {

    /** @return open cases (DETECTADA or NOTIFICADA) of the vehicle */
    List<DatosCaso> openCases(String licensePlate) throws IOException;

    /** @return the case that already has the event as evidence; null if none */
    DatosCaso caseOf(VehicleEvent event) throws IOException;

    /**
     * Creates a case DETECTADA with the event as evidence. Fails if the vehicle already has an open case of
     * that type (e.g. created at the same time by another pod).
     *
     * @param guideId null if the case has no guide
     */
    DatosCaso create(TipoCasoAutomatico caseType, String guideId, VehicleEvent event) throws IOException;

    void addEvidence(long caseId, VehicleEvent event) throws IOException;

    /** Notifies the case to the company: DETECTADA to NOTIFICADA. */
    DatosCaso notify(long caseId, String correlationId) throws IOException;
}

package tse.lab.fiscalizacionautomatica.ruleengine.cases;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.Consumer;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.Detection;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.Situation;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.frontofficefuncionarios.service.gestioncasos.DatosCaso;

/**
 * Assigns each detected event to a single fiscalization case, following the hierarchy of situations:
 * <ul>
 * <li>a case is created for the first situation (in priority order) the vehicle has no open case of
 * (DETECTADA or NOTIFICADA), and then notified to the company;</li>
 * <li>if every situation already has an open case, the event is added as evidence to the one of the
 * highest priority;</li>
 * <li>an event that is already evidence of a case is not assigned again (reprocessing).</li>
 * </ul>
 * If a call fails it throws, so the event stays pending and is evaluated again.
 */
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
            throw new UncheckedIOException(e);
        }
    }

    private void assign(Detection detection) throws IOException {
        VehicleEvent event = detection.event();
        DatosCaso existing = cases.caseOf(event);
        if (existing != null) {
            notifyIfDetected(existing);
            return;
        }
        List<DatosCaso> open = cases.openCases(event.licensePlate());
        for (Situation situation : detection.situations()) {
            if (open.stream().noneMatch(c -> situation.isTypeOf(c.getTipoCaso()))) {
                DatosCaso created = cases.create(situation.caseType(), guideId(detection), event);
                log("Created", created, event);
                notifyIfDetected(created);
                return;
            }
        }
        Situation highest = detection.situations().get(0);
        DatosCaso target = open.stream().filter(c -> highest.isTypeOf(c.getTipoCaso())).findFirst().orElseThrow();
        cases.addEvidence(target.getId(), event);
        log("Added to", target, event);
        notifyIfDetected(target);
    }

    /** Also covers a case whose notification failed before: it is notified with its next event. */
    private void notifyIfDetected(DatosCaso caseData) throws IOException {
        if (DETECTADA.equals(caseData.getEstado())) {
            cases.notify(caseData.getId());
            System.out.printf("Notified case %d (%s)%n", caseData.getId(), caseData.getTipoCaso());
        }
    }

    /** The active trip of the vehicle, if any: the guide of the case. */
    private static String guideId(Detection detection) {
        if (detection.facts() == null || !detection.facts().hasActiveTrip()) {
            return null;
        }
        return detection.facts().activeTrips().get(0).getIdGuia();
    }

    private static void log(String action, DatosCaso caseData, VehicleEvent event) {
        System.out.printf("%s case %d (%s) for %s at %s%n", action, caseData.getId(), caseData.getTipoCaso(),
                event.licensePlate(), event.timestamp());
    }
}

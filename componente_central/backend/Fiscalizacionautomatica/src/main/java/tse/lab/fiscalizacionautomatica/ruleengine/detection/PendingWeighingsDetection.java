package tse.lab.fiscalizacionautomatica.ruleengine.detection;

import java.io.IOException;
import java.util.function.Consumer;
import tse.lab.fiscalizacionautomatica.LogFiscalizacion;
import tse.lab.fiscalizacionautomatica.ruleengine.data.WeighingDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;

/**
 * Evaluates the saved weighings still pending detection, including the ones left by previous runs of
 * the poller. Runs after the poll, in the same process (there is a single poller): no threads needed.
 */
public class PendingWeighingsDetection {

    private static final int BATCH = 1_000;

    private final SituationDetector detector;
    private final WeighingDAO weighingDAO;
    private final Consumer<Detection> onDetected;

    /**
     * @param onDetected what is done with the situations of a weighing (CaseAssigner: assigns it to
     *                   a case). If it fails, the weighing stays pending.
     */
    public PendingWeighingsDetection(SituationDetector detector, WeighingDAO weighingDAO,
            Consumer<Detection> onDetected) {
        this.detector = detector;
        this.weighingDAO = weighingDAO;
        this.onDetected = onDetected;
    }

    /**
     * @return how many weighings could not be evaluated (they stay pending for the next run)
     * @throws IOException if the pending weighings can't be fetched
     */
    public int detectPending() throws IOException {
        int failed = 0;
        for (Weighing weighing : weighingDAO.findPending(BATCH)) {
            try {
                onDetected.accept(detector.detect(weighing));
                weighingDAO.markDetected(weighing);
            } catch (IOException | RuntimeException e) {
                failed++;
                LogFiscalizacion.error(LogFiscalizacion.evento("pesaje.no_evaluado").correlationId(weighing.correlationId())
                        .campo("matricula", weighing.licensePlate()).campo("fechaHora", weighing.timestamp())
                        .errorSinPila(e));
            }
        }
        return failed;
    }
}

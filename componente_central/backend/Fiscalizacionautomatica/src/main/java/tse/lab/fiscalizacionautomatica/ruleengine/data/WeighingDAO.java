package tse.lab.fiscalizacionautomatica.ruleengine.data;

import java.io.IOException;
import java.util.List;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;

/** Storage of the polled weighings, pending detection until evaluated (see {@link RemoteWeighingDAO}). */
public interface WeighingDAO {

    /**
     * Saves the weighings pending detection, ignoring the ones already saved (same license plate and
     * timestamp).
     *
     * @return how many were saved
     */
    int save(List<Weighing> weighings) throws IOException;

    /** @return weighings still pending detection, oldest first */
    List<Weighing> findPending(int limit) throws IOException;

    default long fijarRevisionReglas(Weighing weighing, long revision) throws IOException { return revision; }

    void markDetected(Weighing weighing) throws IOException;
}

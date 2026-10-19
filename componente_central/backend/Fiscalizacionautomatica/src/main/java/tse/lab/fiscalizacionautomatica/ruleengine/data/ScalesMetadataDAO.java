package tse.lab.fiscalizacionautomatica.ruleengine.data;

import java.time.Instant;
import java.util.Optional;

/** Polling state of each scale of the balanzas node (see {@link MongoScalesMetadataDAO}). */
public interface ScalesMetadataDAO {

    /** @return when the scale was last polled successfully; empty if it never was */
    Optional<Instant> findLastTimestamp(long scaleId);

    void saveLastTimestamp(long scaleId, Instant timestamp);
}

package tse.lab.fiscalizacionautomatica.ruleengine.data;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory polling state for tests. */
public class InMemoryScalesMetadataDAO implements ScalesMetadataDAO {

    private final Map<Long, Instant> lastTimestamps = new HashMap<>();

    @Override
    public Optional<Instant> findLastTimestamp(long scaleId) {
        return Optional.ofNullable(lastTimestamps.get(scaleId));
    }

    @Override
    public void saveLastTimestamp(long scaleId, Instant timestamp) {
        lastTimestamps.put(scaleId, timestamp);
    }
}

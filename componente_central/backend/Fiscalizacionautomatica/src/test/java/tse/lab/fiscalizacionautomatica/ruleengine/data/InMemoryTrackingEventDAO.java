package tse.lab.fiscalizacionautomatica.ruleengine.data;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;

/** In-memory storage for tests, discarding duplicates the same way the MongoDB unique index does. */
public class InMemoryTrackingEventDAO implements TrackingEventDAO {

    private record Key(String licensePlate, Instant timestamp) {
    }

    private final Map<Key, TrackingEvent> events = new ConcurrentHashMap<>();

    @Override
    public boolean save(TrackingEvent event) {
        return events.putIfAbsent(new Key(event.licensePlate(), event.timestamp()), event) == null;
    }
}

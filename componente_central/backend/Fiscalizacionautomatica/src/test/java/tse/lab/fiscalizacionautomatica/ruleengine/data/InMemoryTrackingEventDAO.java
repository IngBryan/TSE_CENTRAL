package tse.lab.fiscalizacionautomatica.ruleengine.data;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;

/** In-memory storage for tests, discarding duplicates the same way the MongoDB unique index does. */
public class InMemoryTrackingEventDAO implements TrackingEventDAO {

    private record Key(String licensePlate, Instant timestamp) {
        static Key of(TrackingEvent event) {
            return new Key(event.licensePlate(), event.timestamp());
        }
    }

    private record Stored(TrackingEvent event, Instant receivedAt, boolean detected) {
    }

    private final Map<Key, Stored> events = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryTrackingEventDAO() {
        this(Clock.systemUTC());
    }

    public InMemoryTrackingEventDAO(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean save(TrackingEvent event) {
        return events.putIfAbsent(Key.of(event), new Stored(event, clock.instant(), false)) == null;
    }

    @Override
    public void markDetected(TrackingEvent event) {
        events.computeIfPresent(Key.of(event), (key, stored) -> new Stored(stored.event(), stored.receivedAt(), true));
    }

    @Override
    public List<TrackingEvent> findPendingDetection(Instant receivedBefore, int limit) {
        return events.values().stream()
                .filter(stored -> !stored.detected() && stored.receivedAt().isBefore(receivedBefore))
                .sorted(Comparator.comparing(Stored::receivedAt))
                .limit(limit)
                .map(Stored::event)
                .toList();
    }

    public List<TrackingEvent> events() {
        return events.values().stream().map(Stored::event).toList();
    }

    public boolean isDetected(TrackingEvent event) {
        Stored stored = events.get(Key.of(event));
        return stored != null && stored.detected();
    }
}

package tse.lab.fiscalizacionautomatica.ruleengine.data;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.Situation;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;

/** In-memory storage for tests, discarding duplicates the same way the MongoDB unique index does. */
public class InMemoryTrackingEventDAO implements TrackingEventDAO {

    private record Key(String licensePlate, Instant timestamp) {
        static Key of(TrackingEvent event) {
            return new Key(event.licensePlate(), event.timestamp());
        }
    }

    /** @param situations null while the detection is pending */
    private record Stored(TrackingEvent event, Instant receivedAt, List<Situation> situations) {
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
        return events.putIfAbsent(Key.of(event), new Stored(event, clock.instant(), null)) == null;
    }

    @Override
    public void saveDetection(TrackingEvent event, List<Situation> situations) {
        events.computeIfPresent(Key.of(event), (key, stored) -> stored.situations() != null ? stored
                : new Stored(stored.event(), stored.receivedAt(), List.copyOf(situations)));
    }

    @Override
    public List<TrackingEvent> findPendingDetection(Instant receivedBefore, int limit) {
        return events.values().stream()
                .filter(stored -> stored.situations() == null && stored.receivedAt().isBefore(receivedBefore))
                .sorted(Comparator.comparing(Stored::receivedAt))
                .limit(limit)
                .map(Stored::event)
                .toList();
    }

    public List<TrackingEvent> events() {
        return events.values().stream().map(Stored::event).toList();
    }

    /** @return the detected situations; empty while the detection is pending */
    public Optional<List<Situation>> situationsOf(TrackingEvent event) {
        return Optional.ofNullable(events.get(Key.of(event))).map(Stored::situations);
    }
}

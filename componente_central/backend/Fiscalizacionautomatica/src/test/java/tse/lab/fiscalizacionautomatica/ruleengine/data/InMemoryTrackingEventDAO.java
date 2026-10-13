package tse.lab.fiscalizacionautomatica.ruleengine.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;

/** In-memory storage for tests, discarding duplicates the same way MongoDB does. */
public class InMemoryTrackingEventDAO implements TrackingEventDAO {

    private final Map<String, TrackingEvent> events = new ConcurrentHashMap<>();

    @Override
    public boolean save(TrackingEvent event) {
        return events.putIfAbsent(event.messageId(), event) == null;
    }
}

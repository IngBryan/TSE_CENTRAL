package tse.lab.fiscalizacionautomatica.ruleengine.data;

import java.time.Instant;
import java.util.List;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;

/** Storage of the received tracking events and whether they were evaluated (see {@link MongoTrackingEventDAO}). */
public interface TrackingEventDAO {

    /**
     * Saves the event with its detection pending, unless one with the same license plate and timestamp
     * was already saved.
     *
     * @return {@code true} if it was saved; {@code false} if it already existed (RabbitMQ redelivery)
     */
    boolean save(TrackingEvent event);

    /**
     * Saves the events as {@link #save} does with each one, in a single operation where possible.
     *
     * @return for each event, in order, whether it was saved ({@code false}: it already existed)
     */
    default List<Boolean> saveAll(List<TrackingEvent> events) {
        return events.stream().map(this::save).toList();
    }

    /** Marks the event as evaluated by the detection. */
    void markDetected(TrackingEvent event);

    /** @return events still pending detection that were received before {@code receivedBefore}, oldest first */
    List<TrackingEvent> findPendingDetection(Instant receivedBefore, int limit);
}

package tse.lab.fiscalizacionautomatica.ruleengine.data;

import java.time.Instant;
import java.util.List;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.Situation;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;

/** Storage of the received tracking events and their detection result (see {@link MongoTrackingEventDAO}). */
public interface TrackingEventDAO {

    /**
     * Saves the event with its detection pending, unless one with the same license plate and timestamp
     * was already saved.
     *
     * @return {@code true} if it was saved; {@code false} if it already existed (RabbitMQ redelivery)
     */
    boolean save(TrackingEvent event);

    /** Saves the situations that apply to the event (none is a result too) and marks it as detected. */
    void saveDetection(TrackingEvent event, List<Situation> situations);

    /** @return events still pending detection that were received before {@code receivedBefore}, oldest first */
    List<TrackingEvent> findPendingDetection(Instant receivedBefore, int limit);
}

package tse.lab.fiscalizacionautomatica.ruleengine.data;

import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;

/** Storage of the received tracking events (see {@link MongoTrackingEventDAO}). */
public interface TrackingEventDAO {

    /**
     * Saves the event, unless one with the same {@code messageId} was already saved.
     *
     * @return {@code true} if it was saved; {@code false} if it already existed (RabbitMQ redelivery)
     */
    boolean save(TrackingEvent event);
}

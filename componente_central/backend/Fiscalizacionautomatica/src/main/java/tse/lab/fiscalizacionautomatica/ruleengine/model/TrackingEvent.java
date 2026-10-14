package tse.lab.fiscalizacionautomatica.ruleengine.model;

import java.time.Instant;

/**
 * Position reported by a GPS of the tracking node, received through the tracking.events queue.
 * Identified by (licensePlate, timestamp): a redelivered message has the same key and is not saved
 * twice. {@code externalId} alone can't identify it because tracking generates it in memory and
 * resets it when the service restarts.
 *
 * @param messageId  RabbitMQ message_id (required; kept to trace the event back to its message)
 * @param externalId event id in the tracking node
 */
public record TrackingEvent(
        String messageId,
        long externalId,
        long gpsId,
        String licensePlate,
        double latitude,
        double longitude,
        Instant timestamp) {
}

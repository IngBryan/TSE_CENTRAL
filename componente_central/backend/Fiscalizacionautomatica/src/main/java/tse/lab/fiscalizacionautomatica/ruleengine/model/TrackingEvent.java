package tse.lab.fiscalizacionautomatica.ruleengine.model;

import java.time.Instant;

/**
 * Position reported by a GPS of the tracking node, received through the tracking.events queue.
 *
 * @param messageId RabbitMQ message_id. Identifies the event: it is stable across retries, so it
 *                  is used to discard duplicates. {@code eventId} can't be used for that because
 *                  tracking generates it in memory and resets it when the service restarts.
 * @param eventId   event id in tracking
 */
public record TrackingEvent(
        String messageId,
        long eventId,
        long gpsId,
        String licensePlate,
        double latitude,
        double longitude,
        Instant timestamp) {
}

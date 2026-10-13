package tse.lab.fiscalizacionautomatica.ruleengine.reception;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import tse.lab.fiscalizacionautomatica.ruleengine.data.TrackingEventDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;

/**
 * Reception of a tracking message: parses it, validates it and saves it. It doesn't detect
 * situations: detection runs separately so it doesn't slow down event reception.
 */
public class TrackingEventReceiver {

    public enum Result {
        /** New event, saved. */
        SAVED,
        /** Already received (same message_id): discarded. */
        DUPLICATE,
        /** Can't be parsed or is missing data: discarded. */
        INVALID
    }

    /** Message body, with the field names published by tracking. */
    record TrackingMessage(Long id, Long gpsId, String licensePlate, Double latitude, Double longitude,
            String datetime) {
    }

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final TrackingEventDAO dao;

    public TrackingEventReceiver(TrackingEventDAO dao) {
        this.dao = dao;
    }

    /**
     * @param messageId RabbitMQ message_id
     * @param body      JSON published by tracking
     */
    public Result receive(String messageId, byte[] body) {
        TrackingEvent event;
        try {
            event = parse(messageId, body);
        } catch (IOException | IllegalArgumentException | DateTimeParseException e) {
            return Result.INVALID;
        }
        return dao.save(event) ? Result.SAVED : Result.DUPLICATE;
    }

    private static TrackingEvent parse(String messageId, byte[] body) throws IOException {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("Missing message_id: duplicates can't be detected");
        }
        TrackingMessage m = JSON.readValue(body, TrackingMessage.class);
        if (m == null || m.id() == null || m.gpsId() == null || m.datetime() == null
                || m.licensePlate() == null || m.licensePlate().isBlank()) {
            throw new IllegalArgumentException("Missing required fields");
        }
        Instant timestamp = OffsetDateTime.parse(m.datetime()).toInstant();
        return new TrackingEvent(messageId, m.id(), m.gpsId(), m.licensePlate().strip(),
                m.latitude(), m.longitude(), timestamp);
    }
}

package tse.lab.fiscalizacionautomatica.ruleengine.reception;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.function.Consumer;
import java.util.function.LongPredicate;
import tse.lab.fiscalizacionautomatica.ruleengine.data.TrackingEventDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;

/**
 * Reception of a tracking message: parses it, validates it, saves it and hands it to the detection,
 * which runs in other threads so it doesn't slow down event reception.
 */
public class TrackingEventReceiver {

    public enum Result {
        /** New event, saved. */
        SAVED,
        /** Already received (same license plate and timestamp): discarded. */
        DUPLICATE,
        /** Can't be parsed or is missing data: discarded. */
        INVALID,
        /** Its GPS is not registered as a tracking node, or is inactive: discarded. */
        UNKNOWN_GPS
    }

    /** Message body, with the field names published by tracking. */
    record TrackingMessage(Long id, Long gpsId, String licensePlate, Double latitude, Double longitude,
            String datetime) {
    }

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final TrackingEventDAO dao;
    private final LongPredicate activeGps;
    private final Consumer<TrackingEvent> detection;

    /**
     * @param activeGps whether a gpsId belongs to an active tracking node
     * @param detection queues the detection of a saved event (it must not wait for it)
     */
    public TrackingEventReceiver(TrackingEventDAO dao, LongPredicate activeGps, Consumer<TrackingEvent> detection) {
        this.dao = dao;
        this.activeGps = activeGps;
        this.detection = detection;
    }

    /**
     * @param messageId RabbitMQ message_id (required)
     * @param body      JSON published by tracking
     */
    public Result receive(String messageId, byte[] body) {
        TrackingEvent event;
        try {
            event = parse(messageId, body);
        } catch (IOException | IllegalArgumentException | DateTimeParseException e) {
            return Result.INVALID;
        }
        if (!activeGps.test(event.gpsId())) {
            return Result.UNKNOWN_GPS;
        }
        if (!dao.save(event)) {
            return Result.DUPLICATE;
        }
        detection.accept(event);
        return Result.SAVED;
    }

    private static TrackingEvent parse(String messageId, byte[] body) throws IOException {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("Missing message_id");
        }
        TrackingMessage m = JSON.readValue(body, TrackingMessage.class);
        if (m == null || m.id() == null || m.gpsId() == null || m.datetime() == null
                || m.latitude() == null || m.longitude() == null
                || m.licensePlate() == null || m.licensePlate().isBlank()) {
            throw new IllegalArgumentException("Missing required fields");
        }
        Instant timestamp = OffsetDateTime.parse(m.datetime()).toInstant();
        return new TrackingEvent(messageId, m.id(), m.gpsId(), m.licensePlate().strip(),
                m.latitude(), m.longitude(), timestamp);
    }
}

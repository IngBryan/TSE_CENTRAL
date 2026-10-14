package tse.lab.fiscalizacionautomatica.ruleengine.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;

class MongoTrackingEventDAOTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-10-10T15:30:00Z");
    private static final Instant RECEIVED_AT = Instant.parse("2026-10-10T15:30:02Z");
    private static final TrackingEvent EVENT = new TrackingEvent("msg-1", 7, 1, "SBC1234", -34.9011, -56.1645, TIMESTAMP);

    @Test
    void documentHasTheEventDataAndIsPendingDetection() {
        Document expected = new Document("messageId", "msg-1")
                .append("externalId", 7L)
                .append("gpsId", 1L)
                .append("licensePlate", "SBC1234")
                .append("location", new Document("type", "Point").append("coordinates", List.of(-56.1645, -34.9011)))
                .append("timestamp", Date.from(TIMESTAMP))
                .append("receivedAt", Date.from(RECEIVED_AT))
                .append("detected", false);
        assertEquals(expected, MongoTrackingEventDAO.toDocument(EVENT, RECEIVED_AT));
    }

    @Test
    void readsBackTheSavedEvent() {
        assertEquals(EVENT, MongoTrackingEventDAO.fromDocument(MongoTrackingEventDAO.toDocument(EVENT, RECEIVED_AT)));
    }
}

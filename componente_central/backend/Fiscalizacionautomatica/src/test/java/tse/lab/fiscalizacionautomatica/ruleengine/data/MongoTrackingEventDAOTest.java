package tse.lab.fiscalizacionautomatica.ruleengine.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;

class MongoTrackingEventDAOTest {

    @Test
    void documentHasTheExternalIdAndGeoJsonLocationAndLetsMongoGenerateTheId() {
        Instant timestamp = Instant.parse("2026-10-10T15:30:00Z");
        TrackingEvent event = new TrackingEvent("msg-1", 7, 1, "SBC1234", -34.9011, -56.1645, timestamp);

        Document expected = new Document("messageId", "msg-1")
                .append("externalId", 7L)
                .append("gpsId", 1L)
                .append("licensePlate", "SBC1234")
                .append("location", new Document("type", "Point").append("coordinates", List.of(-56.1645, -34.9011)))
                .append("timestamp", Date.from(timestamp));
        assertEquals(expected, MongoTrackingEventDAO.toDocument(event));
    }
}

package tse.lab.fiscalizacionautomatica.ruleengine.reception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tse.lab.fiscalizacionautomatica.ruleengine.data.InMemoryTrackingEventDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.reception.TrackingEventReceiver.Result;

class TrackingEventReceiverTest {

    private static final String MESSAGE = """
            {"id": 7, "gpsId": 1, "licensePlate": "SBC1234", "latitude": -34.9011,
             "longitude": -56.1645, "datetime": "2026-10-10T15:30:00.000Z"}
            """;

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void savesEventWithMessageData() {
        List<TrackingEvent> saved = new ArrayList<>();
        TrackingEventReceiver receiver = new TrackingEventReceiver(saved::add);

        assertEquals(Result.SAVED, receiver.receive("msg-1", bytes(MESSAGE)));
        assertEquals(new TrackingEvent("msg-1", 7, 1, "SBC1234", -34.9011, -56.1645,
                Instant.parse("2026-10-10T15:30:00Z")), saved.getFirst());
    }

    @Test
    void discardsRedeliveryOfSameMessage() {
        TrackingEventReceiver receiver = new TrackingEventReceiver(new InMemoryTrackingEventDAO());

        assertEquals(Result.SAVED, receiver.receive("msg-1", bytes(MESSAGE)));
        assertEquals(Result.DUPLICATE, receiver.receive("msg-1", bytes(MESSAGE)));
    }

    @Test
    void trackingIdDoesNotIdentifyEvent() {
        // tracking resets its ids on restart: same id with another message_id is another event
        TrackingEventReceiver receiver = new TrackingEventReceiver(new InMemoryTrackingEventDAO());

        assertEquals(Result.SAVED, receiver.receive("msg-1", bytes(MESSAGE)));
        assertEquals(Result.SAVED, receiver.receive("msg-2", bytes(MESSAGE)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void missingMessageIdIsInvalid(String messageId) {
        List<TrackingEvent> saved = new ArrayList<>();
        TrackingEventReceiver receiver = new TrackingEventReceiver(saved::add);

        assertEquals(Result.INVALID, receiver.receive(messageId, bytes(MESSAGE)));
        assertTrue(saved.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "null",
            "{}",
            """
            {"gpsId": 1, "licensePlate": "SBC1234", "latitude": 0, "longitude": 0, "datetime": "2026-10-10T15:30:00Z"}""",
            """
            {"id": 7, "gpsId": 1, "licensePlate": " ", "latitude": 0, "longitude": 0, "datetime": "2026-10-10T15:30:00Z"}""",
            """
            {"id": 7, "gpsId": 1, "licensePlate": "SBC1234", "latitude": 91, "longitude": 0, "datetime": "2026-10-10T15:30:00Z"}""",
            """
            {"id": 7, "gpsId": 1, "licensePlate": "SBC1234", "latitude": 0, "longitude": 0, "datetime": "yesterday"}""",
    })
    void malformedMessageIsInvalid(String json) {
        List<TrackingEvent> saved = new ArrayList<>();
        TrackingEventReceiver receiver = new TrackingEventReceiver(saved::add);

        assertEquals(Result.INVALID, receiver.receive("msg-1", bytes(json)));
        assertTrue(saved.isEmpty());
    }
}

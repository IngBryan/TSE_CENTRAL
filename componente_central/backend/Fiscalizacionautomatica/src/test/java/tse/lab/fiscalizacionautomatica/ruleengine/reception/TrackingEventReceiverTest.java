package tse.lab.fiscalizacionautomatica.ruleengine.reception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;
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

    private static final LongPredicate ANY_GPS = gpsId -> true;

    private final InMemoryTrackingEventDAO dao = new InMemoryTrackingEventDAO();
    /** Events handed to the detection. */
    private final List<TrackingEvent> toDetect = new ArrayList<>();
    private final TrackingEventReceiver receiver = new TrackingEventReceiver(dao, ANY_GPS, toDetect::add);

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void savesEventWithMessageDataAndHandsItToTheDetection() {
        assertEquals(Result.SAVED, receiver.receive("msg-1", bytes(MESSAGE)));

        TrackingEvent expected = new TrackingEvent("msg-1", 7, 1, "SBC1234", -34.9011, -56.1645,
                Instant.parse("2026-10-10T15:30:00Z"));
        assertEquals(List.of(expected), dao.events());
        assertEquals(List.of(expected), toDetect);
    }

    @Test
    void readsDatesInUruguayTime() {
        receiver.receive("msg-1", bytes(MESSAGE.replace("2026-10-10T15:30:00.000Z", "2026-10-12T17:41:00.000-03:00")));

        assertEquals(Instant.parse("2026-10-12T20:41:00Z"), dao.events().getFirst().timestamp());
    }

    @Test
    void discardsRedeliveryOfSameEventWithoutDetectingItAgain() {
        assertEquals(Result.SAVED, receiver.receive("msg-1", bytes(MESSAGE)));
        assertEquals(Result.DUPLICATE, receiver.receive("msg-1", bytes(MESSAGE)));

        assertEquals(1, toDetect.size());
    }

    @Test
    void sameLicensePlateAndTimeIsTheSameEventEvenWithAnotherExternalId() {
        assertEquals(Result.SAVED, receiver.receive("msg-1", bytes(MESSAGE)));
        assertEquals(Result.DUPLICATE, receiver.receive("msg-2", bytes(MESSAGE.replace("\"id\": 7", "\"id\": 8"))));
    }

    @Test
    void externalIdDoesNotIdentifyEvent() {
        // tracking resets its ids on restart: the same id at another time is another event
        assertEquals(Result.SAVED, receiver.receive("msg-1", bytes(MESSAGE)));
        assertEquals(Result.SAVED, receiver.receive("msg-2", bytes(MESSAGE.replace("15:30:00.000Z", "15:31:00.000Z"))));
    }

    @Test
    void discardsEventsOfGpsThatAreNotActiveTrackingNodes() {
        TrackingEventReceiver onlyGps1 = new TrackingEventReceiver(dao, gpsId -> gpsId == 1, toDetect::add);

        assertEquals(Result.UNKNOWN_GPS, onlyGps1.receive("msg-1", bytes(MESSAGE.replace("\"gpsId\": 1", "\"gpsId\": 3"))));
        assertTrue(dao.events().isEmpty());
        assertTrue(toDetect.isEmpty());
        assertEquals(Result.SAVED, onlyGps1.receive("msg-2", bytes(MESSAGE)));
    }

    @Test
    void receivesABatchKeepingTheResultOfEachMessageInOrder() {
        TrackingEventReceiver onlyGps1 = new TrackingEventReceiver(dao, gpsId -> gpsId == 1, toDetect::add);
        String later = MESSAGE.replace("15:30:00.000Z", "15:31:00.000Z");

        List<Result> results = onlyGps1.receiveAll(List.of(
                new TrackingEventReceiver.Message("msg-1", bytes(MESSAGE)),
                new TrackingEventReceiver.Message("msg-2", bytes("not json")),
                new TrackingEventReceiver.Message("msg-3", bytes(MESSAGE.replace("\"gpsId\": 1", "\"gpsId\": 3"))),
                new TrackingEventReceiver.Message("msg-4", bytes(MESSAGE)),
                new TrackingEventReceiver.Message("msg-5", bytes(later))));

        assertEquals(List.of(Result.SAVED, Result.INVALID, Result.UNKNOWN_GPS, Result.DUPLICATE, Result.SAVED), results);
        assertEquals(2, dao.events().size());
        assertEquals(2, toDetect.size());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void missingMessageIdIsInvalid(String messageId) {
        assertEquals(Result.INVALID, receiver.receive(messageId, bytes(MESSAGE)));
        assertTrue(dao.events().isEmpty());
        assertTrue(toDetect.isEmpty());
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
            {"id": 7, "gpsId": 1, "licensePlate": "SBC1234", "longitude": 0, "datetime": "2026-10-10T15:30:00Z"}""",
            """
            {"id": 7, "gpsId": 1, "licensePlate": "SBC1234", "latitude": 0, "datetime": "2026-10-10T15:30:00Z"}""",
            """
            {"id": 7, "gpsId": 1, "licensePlate": "SBC1234", "latitude": 0, "longitude": 0, "datetime": "yesterday"}""",
    })
    void malformedMessageIsInvalid(String json) {
        assertEquals(Result.INVALID, receiver.receive("msg-1", bytes(json)));
        assertTrue(dao.events().isEmpty());
        assertTrue(toDetect.isEmpty());
    }
}

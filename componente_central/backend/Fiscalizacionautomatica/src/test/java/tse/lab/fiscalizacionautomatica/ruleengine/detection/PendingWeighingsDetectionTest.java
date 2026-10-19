package tse.lab.fiscalizacionautomatica.ruleengine.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.data.InMemoryWeighingDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.TripWithoutGuideRule;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;

class PendingWeighingsDetectionTest {

    private static final Weighing SBC1234 = new Weighing(3, 4, "SBC1234", Instant.parse("2026-10-13T10:00:00Z"), 18_450);
    private static final Weighing STU5678 = new Weighing(5, 4, "STU5678", Instant.parse("2026-10-13T11:00:00Z"), 21_000);

    record Result(VehicleEvent event, List<Situation> situations) {
    }

    private final InMemoryWeighingDAO weighingDAO = new InMemoryWeighingDAO();
    private final Map<String, VehicleFacts> central = new HashMap<>();
    private boolean wildFlyDown;
    private final List<Result> detections = new ArrayList<>();

    private final VehicleFactsSource facts = licensePlate -> {
        if (wildFlyDown) {
            throw new IOException("WildFly down");
        }
        return Optional.ofNullable(central.get(licensePlate));
    };
    private final PendingWeighingsDetection detection = new PendingWeighingsDetection(
            new SituationDetector(facts, List.of(new TripWithoutGuideRule())), weighingDAO,
            d -> detections.add(new Result(d.event(), d.situations())));

    @Test
    void evaluatesEveryPendingWeighingAndMarksItAsDetected() throws IOException {
        central.put("SBC1234", SituationDetectorTest.withoutTrips("SBC1234"));
        central.put("STU5678", SituationDetectorTest.withTrip("STU5678", "EN_CURSO"));
        weighingDAO.save(List.of(SBC1234, STU5678));

        assertEquals(0, detection.detectPending());

        assertEquals(List.of(new Result(SBC1234, List.of(Situation.TRIP_WITHOUT_GUIDE)),
                new Result(STU5678, List.of())), detections);
        assertTrue(weighingDAO.isDetected(SBC1234));
        assertTrue(weighingDAO.isDetected(STU5678));
    }

    @Test
    void weighingsThatCanNotBeEvaluatedStayPendingForTheNextRun() throws IOException {
        weighingDAO.save(List.of(SBC1234));
        wildFlyDown = true;

        assertEquals(1, detection.detectPending());
        assertFalse(weighingDAO.isDetected(SBC1234));

        // Next run of the poller
        wildFlyDown = false;
        central.put("SBC1234", SituationDetectorTest.withoutTrips("SBC1234"));

        assertEquals(0, detection.detectPending());
        assertEquals(List.of(new Result(SBC1234, List.of(Situation.TRIP_WITHOUT_GUIDE))), detections);
        assertTrue(weighingDAO.isDetected(SBC1234));
    }

    @Test
    void alreadyDetectedWeighingsAreNotEvaluatedAgain() throws IOException {
        central.put("SBC1234", SituationDetectorTest.withoutTrips("SBC1234"));
        weighingDAO.save(List.of(SBC1234));
        detection.detectPending();

        detection.detectPending();

        assertEquals(1, detections.size());
    }

    @Test
    void failureToGetThePendingWeighingsIsReported() {
        weighingDAO.down = true;

        assertThrows(IOException.class, detection::detectPending);
    }
}

package tse.lab.fiscalizacionautomatica.ruleengine.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.data.InMemoryTrackingEventDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.TripWithoutGuideRule;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.MutableClock;

class TrackingDetectionWorkerTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:00Z");
    private static final Duration PENDING_FOR = Duration.ofMinutes(1);

    record Result(VehicleEvent event, List<Situation> situations) {
    }

    private final MutableClock clock = new MutableClock(NOW);
    private final InMemoryTrackingEventDAO dao = new InMemoryTrackingEventDAO(clock);
    private final Map<String, VehicleFacts> central = new HashMap<>();
    private volatile boolean wildFlyDown;
    private final List<Result> detections = new CopyOnWriteArrayList<>();

    private final VehicleFactsSource facts = licensePlate -> {
        if (wildFlyDown) {
            throw new IOException("WildFly down");
        }
        return Optional.ofNullable(central.get(licensePlate));
    };
    private final TrackingDetectionWorker worker = new TrackingDetectionWorker(
            new SituationDetector(facts, List.of(new TripWithoutGuideRule())), dao,
            d -> detections.add(new Result(d.event(), d.situations())), clock, 2, 100);

    private TrackingEvent saved(String licensePlate) {
        TrackingEvent event = new TrackingEvent("msg-1", 7, 1, licensePlate, -34.9, -56.1, clock.instant());
        dao.save(event);
        return event;
    }

    @Test
    void evaluatesASubmittedEventAndMarksItAsDetected() {
        central.put("SBC1234", SituationDetectorTest.withoutTrips("SBC1234"));
        TrackingEvent event = saved("SBC1234");

        worker.submit(event);
        worker.close();

        assertEquals(List.of(new Result(event, List.of(Situation.TRIP_WITHOUT_GUIDE))), detections);
        assertTrue(dao.isDetected(event));
    }

    @Test
    void anEventWithoutSituationsIsMarkedAsDetectedToo() {
        central.put("STU5678", SituationDetectorTest.withTrip("STU5678", "EN_CURSO"));
        TrackingEvent event = saved("STU5678");

        worker.submit(event);
        worker.close();

        assertEquals(List.of(new Result(event, List.of())), detections);
        assertTrue(dao.isDetected(event));
    }

    @Test
    void ifWildFlyIsDownTheEventStaysPending() {
        wildFlyDown = true;
        TrackingEvent event = saved("SBC1234");

        worker.submit(event);
        worker.close();

        assertEquals(List.of(), detections);
        assertFalse(dao.isDetected(event));
    }

    @Test
    void recoveryQueuesAgainTheEventsPendingForTooLong() {
        // Saved but never evaluated: e.g. the pod restarted, or WildFly was down
        central.put("SBC1234", SituationDetectorTest.withoutTrips("SBC1234"));
        TrackingEvent event = saved("SBC1234");

        clock.advance(Duration.ofMinutes(2));
        worker.recoverPending(PENDING_FOR);
        worker.close();

        assertEquals(List.of(new Result(event, List.of(Situation.TRIP_WITHOUT_GUIDE))), detections);
        assertTrue(dao.isDetected(event));
    }

    @Test
    void recoveryLeavesRecentPendingEventsAlone() {
        central.put("SBC1234", SituationDetectorTest.withoutTrips("SBC1234"));
        TrackingEvent event = saved("SBC1234");

        clock.advance(Duration.ofSeconds(30));
        worker.recoverPending(PENDING_FOR);
        worker.close();

        assertEquals(List.of(), detections);
        assertFalse(dao.isDetected(event));
    }
}

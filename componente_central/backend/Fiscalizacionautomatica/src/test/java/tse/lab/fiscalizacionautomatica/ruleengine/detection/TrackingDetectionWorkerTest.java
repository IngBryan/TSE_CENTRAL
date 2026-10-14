package tse.lab.fiscalizacionautomatica.ruleengine.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.data.InMemoryTrackingEventDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.TripWithoutGuideRule;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.MutableClock;

class TrackingDetectionWorkerTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:00Z");
    private static final Duration PENDING_FOR = Duration.ofMinutes(1);

    private final MutableClock clock = new MutableClock(NOW);
    private final InMemoryTrackingEventDAO dao = new InMemoryTrackingEventDAO(clock);
    private final Map<String, VehicleFacts> central = new HashMap<>();
    private volatile boolean wildFlyDown;

    private final VehicleFactsSource facts = licensePlate -> {
        if (wildFlyDown) {
            throw new IOException("WildFly down");
        }
        return Optional.ofNullable(central.get(licensePlate));
    };
    private final TrackingDetectionWorker worker = new TrackingDetectionWorker(
            new SituationDetector(facts, List.of(new TripWithoutGuideRule())), dao, clock, 2, 100);

    private TrackingEvent saved(String licensePlate) {
        TrackingEvent event = new TrackingEvent("msg-1", 7, 1, licensePlate, -34.9, -56.1, clock.instant());
        dao.save(event);
        return event;
    }

    @Test
    void savesTheSituationsOfASubmittedEvent() {
        central.put("SBC1234", SituationDetectorTest.withoutTrips("SBC1234"));
        TrackingEvent event = saved("SBC1234");

        worker.submit(event);
        worker.close();

        assertEquals(Optional.of(List.of(Situation.TRIP_WITHOUT_GUIDE)), dao.situationsOf(event));
    }

    @Test
    void anEventWithoutSituationsIsMarkedAsDetectedToo() {
        central.put("STU5678", SituationDetectorTest.withTrip("STU5678", "EN_CURSO"));
        TrackingEvent event = saved("STU5678");

        worker.submit(event);
        worker.close();

        assertEquals(Optional.of(List.of()), dao.situationsOf(event));
    }

    @Test
    void ifWildFlyIsDownTheEventStaysPending() {
        wildFlyDown = true;
        TrackingEvent event = saved("SBC1234");

        worker.submit(event);
        worker.close();

        assertEquals(Optional.empty(), dao.situationsOf(event));
    }

    @Test
    void recoveryQueuesAgainTheEventsPendingForTooLong() {
        // Saved but never evaluated: e.g. the pod restarted, or WildFly was down
        central.put("SBC1234", SituationDetectorTest.withoutTrips("SBC1234"));
        TrackingEvent event = saved("SBC1234");

        clock.advance(Duration.ofMinutes(2));
        worker.recoverPending(PENDING_FOR);
        worker.close();

        assertEquals(Optional.of(List.of(Situation.TRIP_WITHOUT_GUIDE)), dao.situationsOf(event));
    }

    @Test
    void recoveryLeavesRecentPendingEventsAlone() {
        central.put("SBC1234", SituationDetectorTest.withoutTrips("SBC1234"));
        TrackingEvent event = saved("SBC1234");

        clock.advance(Duration.ofSeconds(30));
        worker.recoverPending(PENDING_FOR);
        worker.close();

        assertEquals(Optional.empty(), dao.situationsOf(event));
    }
}

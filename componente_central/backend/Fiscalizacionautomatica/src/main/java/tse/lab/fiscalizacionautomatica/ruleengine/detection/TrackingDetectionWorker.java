package tse.lab.fiscalizacionautomatica.ruleengine.detection;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;
import tse.lab.fiscalizacionautomatica.ruleengine.data.TrackingEventDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;

/**
 * Runs the detection of the saved tracking events in background threads, so it doesn't slow down
 * their reception. The events of a vehicle always go to the same thread ("lane"): they are evaluated
 * in order and never at the same time. An event that can't be evaluated (WildFly down, full queue,
 * pod restarted) stays pending in MongoDB and the periodic recovery queues it again.
 */
public class TrackingDetectionWorker implements AutoCloseable {

    private static final int RECOVERY_BATCH = 500;
    private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(10);

    private record Key(String licensePlate, Instant timestamp) {
    }

    private final SituationDetector detector;
    private final TrackingEventDAO dao;
    private final BiConsumer<VehicleEvent, List<Situation>> onDetected;
    private final Clock clock;
    private final List<ThreadPoolExecutor> lanes;
    /** Events queued or being evaluated: the recovery doesn't queue them twice. */
    private final Set<Key> inFlight = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService recovery = Executors.newSingleThreadScheduledExecutor(
            daemonThreads("detection-recovery"));

    /**
     * @param onDetected what is done with the situations of an event (only logged for now; it will
     *                   create the cases). If it fails, the event stays pending.
     */
    public TrackingDetectionWorker(SituationDetector detector, TrackingEventDAO dao,
            BiConsumer<VehicleEvent, List<Situation>> onDetected, Clock clock, int lanes, int queueCapacityPerLane) {
        this.detector = detector;
        this.dao = dao;
        this.onDetected = onDetected;
        this.clock = clock;
        this.lanes = IntStream.range(0, lanes)
                .mapToObj(i -> new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>(queueCapacityPerLane), daemonThreads("detection-" + i)))
                .toList();
    }

    /** Queues the detection of a saved event without waiting for it. */
    public void submit(TrackingEvent event) {
        Key key = new Key(event.licensePlate(), event.timestamp());
        if (!inFlight.add(key)) {
            return;
        }
        ThreadPoolExecutor lane = lanes.get(Math.floorMod(event.licensePlate().hashCode(), lanes.size()));
        try {
            lane.execute(() -> {
                try {
                    detect(event);
                } finally {
                    inFlight.remove(key);
                }
            });
        } catch (RejectedExecutionException e) {
            // Full queue or shutting down: it stays pending and the recovery queues it again
            inFlight.remove(key);
        }
    }

    void detect(TrackingEvent event) {
        try {
            onDetected.accept(event, detector.detect(event));
            dao.markDetected(event);
        } catch (IOException | RuntimeException e) {
            System.err.printf("Could not evaluate the event of %s at %s, it stays pending: %s%n",
                    event.licensePlate(), event.timestamp(), e.getMessage());
        }
    }

    /** Every {@code period} (starting now), queues again the events pending for more than {@code pendingFor}. */
    public void recoverPendingEvery(Duration period, Duration pendingFor) {
        recovery.scheduleWithFixedDelay(() -> recoverPending(pendingFor), 0, period.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    void recoverPending(Duration pendingFor) {
        try {
            dao.findPendingDetection(clock.instant().minus(pendingFor), RECOVERY_BATCH).forEach(this::submit);
        } catch (RuntimeException e) {
            System.err.println("Could not look for events pending detection: " + e.getMessage());
        }
    }

    /** Stops the recovery and waits a bounded time for the queued detections; the rest stay pending. */
    @Override
    public void close() {
        recovery.shutdownNow();
        lanes.forEach(ThreadPoolExecutor::shutdown);
        long deadline = System.nanoTime() + SHUTDOWN_WAIT.toNanos();
        try {
            for (ThreadPoolExecutor lane : lanes) {
                lane.awaitTermination(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        lanes.forEach(ThreadPoolExecutor::shutdownNow);
    }

    private static ThreadFactory daemonThreads(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }
}

package tse.lab.fiscalizacionautomatica.ruleengine.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.MutableClock;

class NodeCommunicationsTest {

    private static final Instant NOW = Instant.parse("2026-10-19T13:00:00Z");

    private final MutableClock clock = new MutableClock(NOW);
    private final List<Map<Long, Instant>> batches = new ArrayList<>();
    private boolean wildFlyDown;
    private boolean unexpectedFailure;

    private final NodeCommunications communications = new NodeCommunications(batch -> {
        if (wildFlyDown) {
            throw new IOException("WildFly down");
        }
        if (unexpectedFailure) {
            throw new IllegalStateException("bug");
        }
        batches.add(batch);
    }, clock);

    @Test
    void sendsTheLastCommunicationOfEachNodeInOneBatch() {
        communications.record(1);
        clock.advance(Duration.ofSeconds(10));
        communications.record(1);
        communications.record(2);

        communications.flush();

        Instant later = NOW.plusSeconds(10);
        assertEquals(List.of(Map.of(1L, later, 2L, later)), batches);
    }

    @Test
    void nothingIsSentWithoutNewCommunications() {
        communications.record(1);
        communications.flush();
        communications.flush();

        assertEquals(1, batches.size());
    }

    @Test
    void aFailedBatchIsSentWithTheNextOne() {
        communications.record(1);
        communications.record(2);
        wildFlyDown = true;
        communications.flush();

        wildFlyDown = false;
        clock.advance(Duration.ofSeconds(60));
        communications.record(2);
        communications.flush();

        assertEquals(List.of(Map.of(1L, NOW, 2L, NOW.plusSeconds(60))), batches);
    }

    @Test
    void anUnexpectedFailureAlsoKeepsTheBatch() {
        communications.record(1);
        unexpectedFailure = true;
        communications.flush();

        unexpectedFailure = false;
        communications.flush();

        assertEquals(List.of(Map.of(1L, NOW)), batches);
    }

    @Test
    void flushesPeriodically() throws InterruptedException {
        communications.record(5);

        communications.flushEvery(Duration.ofMillis(10));

        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (batches.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        communications.close();
        assertEquals(Map.of(5L, NOW), batches.get(0));
    }

    @Test
    void closingWaitsForTheFlushInProgressInsteadOfInterruptingIt() throws InterruptedException {
        CountDownLatch reporting = new CountDownLatch(1);
        List<Map<Long, Instant>> sent = new ArrayList<>();
        NodeCommunications slow = new NodeCommunications(batch -> {
            reporting.countDown();
            try {
                Thread.sleep(200); // the remote call in flight
            } catch (InterruptedException e) {
                throw new IOException("interrupted");
            }
            sent.add(batch);
        }, clock);
        slow.record(7);

        slow.flushEvery(Duration.ofMillis(1));
        reporting.await();
        slow.close();

        assertEquals(List.of(Map.of(7L, NOW)), sent);
    }

    @Test
    void closingSendsWhatIsLeft() {
        communications.record(3);

        communications.close();

        assertEquals(List.of(Map.of(3L, NOW)), batches);
    }
}

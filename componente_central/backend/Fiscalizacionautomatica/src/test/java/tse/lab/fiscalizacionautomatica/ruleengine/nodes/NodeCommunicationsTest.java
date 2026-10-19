package tse.lab.fiscalizacionautomatica.ruleengine.nodes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.MutableClock;

class NodeCommunicationsTest {

    private static final Instant NOW = Instant.parse("2026-10-19T13:00:00Z");

    private final MutableClock clock = new MutableClock(NOW);
    private final List<Map<Long, Instant>> batches = new ArrayList<>();
    private boolean wildFlyDown;

    private final NodeCommunications communications = new NodeCommunications(batch -> {
        if (wildFlyDown) {
            throw new IOException("WildFly down");
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
    void closingSendsWhatIsLeft() {
        communications.record(3);

        communications.close();

        assertEquals(List.of(Map.of(3L, NOW)), batches);
    }
}

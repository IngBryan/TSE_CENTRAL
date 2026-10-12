package tse.lab.fiscalizacionautomatica.ruleengine.polling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.WeighingsPoller.PollResult;

class WeighingsPollerTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final Duration WINDOW = Duration.ofHours(1);

    record Request(long scaleId, String licensePlate, Instant from, Instant to) {
    }

    /** Records the requested ranges; the scales in {@code failing} throw. */
    static class FakeScalesApi implements ScalesApi {

        final List<Request> requests = new ArrayList<>();
        final Set<Long> failing = new HashSet<>();

        @Override
        public List<Weighing> listWeighings(long scaleId, String licensePlate, Instant from, Instant to)
                throws IOException {
            requests.add(new Request(scaleId, licensePlate, from, to));
            if (failing.contains(scaleId)) {
                throw new IOException("scale " + scaleId + " unavailable");
            }
            return List.of(new Weighing(scaleId * 10, scaleId, "SBC1234", to, 18_000));
        }
    }

    private final FakeScalesApi api = new FakeScalesApi();
    private final MutableClock clock = new MutableClock(NOW);
    private final WeighingsPoller poller = new WeighingsPoller(List.of(1L, 2L), "SBC1234", WINDOW, api, clock);

    @Test
    void queriesEveryScaleForTheWindowEndingNow() {
        PollResult result = poller.poll();

        Instant from = NOW.minus(WINDOW);
        assertEquals(List.of(new Request(1, "SBC1234", from, NOW), new Request(2, "SBC1234", from, NOW)), api.requests);
        assertEquals(2, result.weighings().size());
        assertEquals(List.of(), result.failedScaleIds());
    }

    @Test
    void reportsTheScalesThatFailedAndKeepsTheRest() {
        api.failing.add(2L);

        PollResult result = poller.poll();

        assertEquals(List.of(2L), result.failedScaleIds());
        assertEquals(List.of(new Weighing(10, 1, "SBC1234", NOW, 18_000)), result.weighings());
    }

    @Test
    void eachPollIsIndependent() {
        // No state between runs: a later poll (even after a failure) only depends on the current time
        api.failing.add(2L);
        poller.poll();
        clock.advance(Duration.ofMinutes(5));
        api.failing.clear();
        api.requests.clear();

        poller.poll();

        Instant now = NOW.plus(Duration.ofMinutes(5));
        Instant from = now.minus(WINDOW);
        assertEquals(List.of(new Request(1, "SBC1234", from, now), new Request(2, "SBC1234", from, now)), api.requests);
    }
}

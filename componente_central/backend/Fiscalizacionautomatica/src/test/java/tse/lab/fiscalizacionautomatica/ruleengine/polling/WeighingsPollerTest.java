package tse.lab.fiscalizacionautomatica.ruleengine.polling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.data.InMemoryScalesMetadataDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.data.InMemoryWeighingDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.WeighingsPoller.PollResult;

class WeighingsPollerTest {

    // 09:00 in Uruguay (UTC-3)
    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final Instant START_OF_TODAY = Instant.parse("2026-10-10T03:00:00Z");

    record Request(long scaleId, Instant from, Instant to) {
    }

    /** Records the requested ranges; the scales in {@code failing} throw. */
    static class FakeScalesApi implements ScalesApi {

        final List<Request> requests = new ArrayList<>();
        final Set<Long> failing = new HashSet<>();

        @Override
        public List<Weighing> listWeighings(long scaleId, Instant from, Instant to)
                throws IOException {
            requests.add(new Request(scaleId, from, to));
            if (failing.contains(scaleId)) {
                throw new IOException("scale " + scaleId + " unavailable");
            }
            // A vehicle is weighed in each scale at a different moment
            return List.of(new Weighing(scaleId * 10, scaleId, "SBC1234", to.minusSeconds(scaleId), 18_000));
        }
    }

    private final FakeScalesApi api = new FakeScalesApi();
    private final InMemoryScalesMetadataDAO metadata = new InMemoryScalesMetadataDAO();
    private final MutableClock clock = new MutableClock(NOW);
    private final InMemoryWeighingDAO weighingDAO = new InMemoryWeighingDAO();
    private final WeighingsPoller poller = new WeighingsPoller(List.of(1L, 2L), api, weighingDAO, metadata,
            clock);

    @Test
    void firstPollStartsAtTheBeginningOfTodayInUruguay() {
        PollResult result = poller.poll();

        assertEquals(List.of(new Request(1, START_OF_TODAY, NOW),
                new Request(2, START_OF_TODAY, NOW)), api.requests);
        assertEquals(2, result.weighings().size());
        assertEquals(List.of(), result.failedScaleIds());
        assertEquals(Optional.of(NOW), metadata.findLastTimestamp(1));
        assertEquals(Optional.of(NOW), metadata.findLastTimestamp(2));
    }

    @Test
    void beforeMidnightInUruguayTodayIsStillTheDayBefore() {
        // 23:30 of October 9 in Uruguay, already October 10 in UTC
        MutableClock lateClock = new MutableClock(Instant.parse("2026-10-10T02:30:00Z"));

        new WeighingsPoller(List.of(1L), api, weighingDAO, metadata, lateClock).poll();

        assertEquals(Instant.parse("2026-10-09T03:00:00Z"), api.requests.getFirst().from());
    }

    @Test
    void eachScaleIsQueriedFromItsOwnLastTimestamp() {
        Instant lastOfScale1 = NOW.minus(Duration.ofMinutes(5));
        Instant lastOfScale2 = NOW.minus(Duration.ofHours(1));
        metadata.saveLastTimestamp(1, lastOfScale1);
        metadata.saveLastTimestamp(2, lastOfScale2);

        poller.poll();

        assertEquals(List.of(new Request(1, lastOfScale1, NOW),
                new Request(2, lastOfScale2, NOW)), api.requests);
    }

    @Test
    void eachPollStartsWhereThePreviousOneEnded() {
        poller.poll();
        clock.advance(Duration.ofMinutes(5));
        api.requests.clear();

        poller.poll();

        Instant now = NOW.plus(Duration.ofMinutes(5));
        assertEquals(List.of(new Request(1, NOW, now), new Request(2, NOW, now)),
                api.requests);
    }

    @Test
    void aFailedScaleRepeatsItsRangeWithoutHoldingBackTheOthers() {
        Instant last = NOW.minus(Duration.ofMinutes(5));
        metadata.saveLastTimestamp(1, last);
        metadata.saveLastTimestamp(2, last);
        api.failing.add(2L);

        PollResult result = poller.poll();

        assertEquals(List.of(2L), result.failedScaleIds());
        assertEquals(List.of(new Weighing(10, 1, "SBC1234", NOW.minusSeconds(1), 18_000)), result.weighings());
        assertEquals(Optional.of(NOW), metadata.findLastTimestamp(1));
        assertEquals(Optional.of(last), metadata.findLastTimestamp(2));

        clock.advance(Duration.ofMinutes(5));
        api.failing.clear();
        api.requests.clear();
        poller.poll();

        Instant now = NOW.plus(Duration.ofMinutes(5));
        assertEquals(List.of(new Request(1, NOW, now), new Request(2, last, now)),
                api.requests);
    }

    @Test
    void savesTheFetchedWeighingsPendingDetection() throws IOException {
        PollResult result = poller.poll();

        List<Weighing> fetched = List.of(new Weighing(10, 1, "SBC1234", NOW.minusSeconds(1), 18_000),
                new Weighing(20, 2, "SBC1234", NOW.minusSeconds(2), 18_000));
        assertEquals(fetched, weighingDAO.saved());
        assertEquals(fetched.reversed(), weighingDAO.findPending(100));
        assertEquals(2, result.saved());
    }

    @Test
    void ifTheWeighingsCanNotBeSavedTheTimestampDoesNotMove() {
        weighingDAO.down = true;

        PollResult result = poller.poll();

        assertEquals(List.of(1L, 2L), result.failedScaleIds());
        assertEquals(Optional.empty(), metadata.findLastTimestamp(1));
        assertEquals(Optional.empty(), metadata.findLastTimestamp(2));
    }
}

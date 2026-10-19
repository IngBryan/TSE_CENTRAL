package tse.lab.fiscalizacionautomatica.ruleengine.nodes;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Last successful communication with each node (ultimaComunicacionExitosa), kept in memory and sent to
 * the backoffice in batches, so receiving an event doesn't call WildFly. A batch that fails is merged
 * back and sent with the next one.
 */
public class NodeCommunications implements AutoCloseable {

    /** Where the batches go: {@link ActiveNodesClient#reportGpsCommunications(Map)} in production. */
    public interface Target {
        void report(Map<Long, Instant> lastByExternalId) throws IOException;
    }

    private static final Duration CLOSE_WAIT = Duration.ofSeconds(5);

    private final Target target;
    private final Clock clock;
    private final Map<Long, Instant> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "node-communications");
        thread.setDaemon(true);
        return thread;
    });

    public NodeCommunications(Target target, Clock clock) {
        this.target = target;
        this.clock = clock;
    }

    /** The central received data from the node now. */
    public void record(long externalId) {
        pending.merge(externalId, clock.instant(), NodeCommunications::latest);
    }

    public void flushEvery(Duration period) {
        scheduler.scheduleWithFixedDelay(this::flush, period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
    }

    void flush() {
        Map<Long, Instant> batch = new HashMap<>();
        for (Long externalId : pending.keySet()) {
            Instant last = pending.remove(externalId);
            if (last != null) {
                batch.put(externalId, last);
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        try {
            target.report(batch);
        } catch (IOException | RuntimeException e) {
            batch.forEach((externalId, last) -> pending.merge(externalId, last, NodeCommunications::latest));
            System.err.println("Could not record the last communication of the nodes, retrying with the next batch: "
                    + e.getMessage());
        }
    }

    /**
     * Stops the periodic flush, waiting for one in progress (interrupting it could cut its remote call and
     * put its batch back after the last flush), and sends what is left.
     */
    @Override
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(CLOSE_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        flush();
    }

    private static Instant latest(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}

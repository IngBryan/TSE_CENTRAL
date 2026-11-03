package tse.lab.fiscalizacionautomatica.ruleengine.nodes;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import tse.lab.fiscalizacionautomatica.LogFiscalizacion;

/**
 * Active GPS ids, kept in memory so checking a tracking event doesn't call WildFly. The list is
 * refreshed periodically; if a refresh fails, the last list is kept.
 */
public class ActiveGpsRegistry implements AutoCloseable {

    /** Where the list comes from: {@link ActiveNodesClient#findActiveGpsIds()} in production. */
    public interface Source {
        Set<Long> findActiveGpsIds() throws IOException;
    }

    private final Source source;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "active-gps-refresh");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Set<Long> activeGpsIds = Set.of();

    public ActiveGpsRegistry(Source source) {
        this.source = source;
    }

    /** Loads the list; fails if it can't be fetched (the consumer doesn't start without it). */
    public void load() throws IOException {
        activeGpsIds = Set.copyOf(source.findActiveGpsIds());
    }

    public void refreshEvery(Duration period) {
        scheduler.scheduleWithFixedDelay(this::refresh, period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
    }

    void refresh() {
        try {
            load();
        } catch (IOException | RuntimeException e) {
            LogFiscalizacion.error(LogFiscalizacion.evento("gps_activos.refresco_fallido").error(e));
        }
    }

    public boolean isActive(long gpsId) {
        return activeGpsIds.contains(gpsId);
    }

    public int size() {
        return activeGpsIds.size();
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}

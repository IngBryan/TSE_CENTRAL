package tse.lab.fiscalizacionautomatica.ruleengine.polling;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;

/**
 * Fetches the weighings of a vehicle in the given scales, in a time window ending now. Stateless:
 * every run is independent, so it can be launched periodically by a cron (each run is a new process).
 */
public class WeighingsPoller {

    /** @param failedScaleIds scales that could not be polled */
    public record PollResult(List<Weighing> weighings, List<Long> failedScaleIds) {
    }

    private final List<Long> scaleIds;
    private final String licensePlate;
    private final Duration window;
    private final ScalesApi api;
    private final Clock clock;

    public WeighingsPoller(List<Long> scaleIds, String licensePlate, Duration window, ScalesApi api, Clock clock) {
        this.scaleIds = scaleIds;
        this.licensePlate = licensePlate;
        this.window = window;
        this.api = api;
        this.clock = clock;
    }

    public PollResult poll() {
        Instant to = clock.instant();
        Instant from = to.minus(window);
        List<Weighing> weighings = new ArrayList<>();
        List<Long> failedScaleIds = new ArrayList<>();
        for (long scaleId : scaleIds) {
            try {
                weighings.addAll(api.listWeighings(scaleId, licensePlate, from, to));
            } catch (IOException e) {
                failedScaleIds.add(scaleId);
            }
        }
        return new PollResult(weighings, failedScaleIds);
    }
}

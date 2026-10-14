package tse.lab.fiscalizacionautomatica.ruleengine.polling;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import tse.lab.fiscalizacionautomatica.ruleengine.data.ScalesMetadataDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;

/**
 * Fetches the weighings of a vehicle in the given scales, each one since its last successful poll
 * (stored in MongoDB) until now. The process keeps no state, so it can be launched periodically by
 * a cron.
 */
public class WeighingsPoller {

    private static final ZoneId URUGUAY = ZoneId.of("America/Montevideo");

    /** @param failedScaleIds scales that could not be polled */
    public record PollResult(Instant to, List<Weighing> weighings, List<Long> failedScaleIds) {
    }

    private final List<Long> scaleIds;
    private final String licensePlate;
    private final ScalesApi api;
    private final ScalesMetadataDAO metadata;
    private final Clock clock;

    public WeighingsPoller(List<Long> scaleIds, String licensePlate, ScalesApi api, ScalesMetadataDAO metadata,
            Clock clock) {
        this.scaleIds = scaleIds;
        this.licensePlate = licensePlate;
        this.api = api;
        this.metadata = metadata;
        this.clock = clock;
    }

    /**
     * Each scale is queried from its last successful poll (the start of today in Uruguay if there is
     * none) until now, and its last timestamp moves forward only if it answered: a failed scale
     * repeats its range in the next run, without holding back the others.
     */
    public PollResult poll() {
        Instant to = clock.instant();
        List<Weighing> weighings = new ArrayList<>();
        List<Long> failedScaleIds = new ArrayList<>();
        for (long scaleId : scaleIds) {
            Instant from = metadata.findLastTimestamp(scaleId).orElseGet(() -> startOfDay(to));
            try {
                weighings.addAll(api.listWeighings(scaleId, licensePlate, from, to));
                metadata.saveLastTimestamp(scaleId, to);
            } catch (IOException e) {
                failedScaleIds.add(scaleId);
            }
        }
        return new PollResult(to, weighings, failedScaleIds);
    }

    private static Instant startOfDay(Instant now) {
        return LocalDate.ofInstant(now, URUGUAY).atStartOfDay(URUGUAY).toInstant();
    }
}

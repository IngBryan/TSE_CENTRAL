package tse.lab.fiscalizacionautomatica.ruleengine.polling;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import tse.lab.fiscalizacionautomatica.ruleengine.data.ScalesMetadataDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.data.WeighingDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;

/**
 * Fetches the weighings of every vehicle in the given scales, each one since its last successful poll
 * (stored in MongoDB) until now, and saves them pending detection. The process keeps no state, so it
 * can be launched periodically by a cron.
 */
public class WeighingsPoller {

    private static final ZoneId URUGUAY = ZoneId.of("America/Montevideo");

    /**
     * @param weighings      weighings fetched (including the ones already saved by a previous run)
     * @param saved          how many of them were new
     * @param failedScaleIds scales that could not be polled or whose weighings could not be saved
     */
    public record PollResult(Instant to, List<Weighing> weighings, int saved, List<Long> failedScaleIds) {
    }

    private final List<Long> scaleIds;
    private final ScalesApi api;
    private final WeighingDAO weighingDAO;
    private final ScalesMetadataDAO metadata;
    private final Clock clock;

    public WeighingsPoller(List<Long> scaleIds, ScalesApi api, WeighingDAO weighingDAO,
            ScalesMetadataDAO metadata, Clock clock) {
        this.scaleIds = scaleIds;
        this.api = api;
        this.weighingDAO = weighingDAO;
        this.metadata = metadata;
        this.clock = clock;
    }

    /**
     * Each scale is queried from its last successful poll (the start of today in Uruguay if there is
     * none) until now. Its last timestamp moves forward only once its weighings are saved: a failed
     * scale repeats its range in the next run, without holding back the others.
     */
    public PollResult poll() {
        Instant to = clock.instant();
        List<Weighing> weighings = new ArrayList<>();
        int saved = 0;
        List<Long> failedScaleIds = new ArrayList<>();
        for (long scaleId : scaleIds) {
            Instant from = metadata.findLastTimestamp(scaleId).orElseGet(() -> startOfDay(to));
            try {
                List<Weighing> fetched = api.listWeighings(scaleId, from, to);
                saved += weighingDAO.save(fetched);
                metadata.saveLastTimestamp(scaleId, to);
                weighings.addAll(fetched);
            } catch (IOException e) {
                failedScaleIds.add(scaleId);
            }
        }
        return new PollResult(to, weighings, saved, failedScaleIds);
    }

    private static Instant startOfDay(Instant now) {
        return LocalDate.ofInstant(now, URUGUAY).atStartOfDay(URUGUAY).toInstant();
    }
}

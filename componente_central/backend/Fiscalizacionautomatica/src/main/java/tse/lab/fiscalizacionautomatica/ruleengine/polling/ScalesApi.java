package tse.lab.fiscalizacionautomatica.ruleengine.polling;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;

/** API of the balanzas node (see {@link ScalesApiClient}). */
public interface ScalesApi {

    /** Weighings of every vehicle in a scale between {@code from} and {@code to} (both inclusive). */
    List<Weighing> listWeighings(long scaleId, Instant from, Instant to) throws IOException;
}

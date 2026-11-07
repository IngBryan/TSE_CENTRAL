package tse.lab.fiscalizacionautomatica.ruleengine.detection;

import java.io.IOException;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;

/** Detection of one situation. Remote failures throw IOException so the event stays pending. */
public interface SituationRule {

    Situation situation();

    boolean appliesTo(VehicleEvent event, VehicleFacts facts) throws IOException;
}

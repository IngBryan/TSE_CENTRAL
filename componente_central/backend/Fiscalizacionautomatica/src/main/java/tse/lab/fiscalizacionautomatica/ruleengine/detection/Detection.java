package tse.lab.fiscalizacionautomatica.ruleengine.detection;

import java.util.List;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;

/**
 * Result of evaluating a vehicle event.
 *
 * @param facts      what the central knows about the vehicle; null if it is not registered
 * @param situations the ones that apply, in priority order
 */
public record Detection(VehicleEvent event, VehicleFacts facts, List<Situation> situations) {
}

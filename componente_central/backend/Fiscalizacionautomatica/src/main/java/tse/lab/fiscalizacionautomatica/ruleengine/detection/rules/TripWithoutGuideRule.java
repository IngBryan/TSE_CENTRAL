package tse.lab.fiscalizacionautomatica.ruleengine.detection.rules;

import tse.lab.fiscalizacionautomatica.ruleengine.detection.Situation;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.SituationRule;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.VehicleFacts;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;

/**
 * Trip without guide: a tracking or weighing event of a vehicle without an assigned or ongoing trip
 * (no guide ASIGNADA or EN_CURSO).
 */
public class TripWithoutGuideRule implements SituationRule {

    @Override
    public Situation situation() {
        return Situation.TRIP_WITHOUT_GUIDE;
    }

    @Override
    public boolean appliesTo(VehicleEvent event, VehicleFacts facts) {
        return !facts.hasActiveTrip();
    }
}

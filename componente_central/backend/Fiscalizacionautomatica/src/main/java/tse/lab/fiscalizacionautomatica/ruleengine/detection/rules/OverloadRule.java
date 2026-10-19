package tse.lab.fiscalizacionautomatica.ruleengine.detection.rules;

import tse.lab.fiscalizacionautomatica.ruleengine.detection.Situation;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.SituationRule;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.VehicleFacts;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;

/**
 * Overload: a weighing heavier than the weight of the vehicle plus its declared load capacity. Only for a
 * vehicle with an active trip (without a guide it is a trip without guide). The tolerance will be
 * configurable by the administrators; for now any excess counts.
 */
public class OverloadRule implements SituationRule {

    @Override
    public Situation situation() {
        return Situation.OVERLOAD;
    }

    @Override
    public boolean appliesTo(VehicleEvent event, VehicleFacts facts) {
        if (!(event instanceof Weighing weighing) || !facts.hasActiveTrip()) {
            return false;
        }
        long maximum = facts.vehicle().getPesoKg() + facts.vehicle().getCapacidadCargaKg();
        return maximum < weighing.weightKg();
    }
}

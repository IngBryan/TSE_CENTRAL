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
        return facts.hasActiveTrip() && excedePeso(event, facts);
    }

    /** Condición de peso; el detector con contexto temporal valida la Guía histórica por separado. */
    public boolean excedePeso(VehicleEvent event, VehicleFacts facts) {
        if (!(event instanceof Weighing weighing)) {
            return false;
        }
        long maximum = (long) facts.vehicle().getPesoKg() + facts.vehicle().getCapacidadCargaKg();
        return maximum < weighing.weightKg();
    }
}

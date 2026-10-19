package tse.lab.fiscalizacionautomatica.ruleengine.detection;

import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;

/** Detection of one situation. Adding a situation means adding a rule to the detector. */
public interface SituationRule {

    Situation situation();

    boolean appliesTo(VehicleEvent event, VehicleFacts facts);
}

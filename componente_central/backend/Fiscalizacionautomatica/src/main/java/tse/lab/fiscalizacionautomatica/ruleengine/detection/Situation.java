package tse.lab.fiscalizacionautomatica.ruleengine.detection;

/**
 * Possible non-compliance situations, in priority order (the declaration order is the priority).
 * Without an active trip only TRIP_WITHOUT_GUIDE and VEHICLE_WITHOUT_VALID_PERMIT can apply.
 */
public enum Situation {
    TRIP_WITHOUT_GUIDE,
    OVERLOAD,
    VEHICLE_WITHOUT_VALID_PERMIT,
    ROUTE_DEVIATION
}

package tse.lab.fiscalizacionautomatica.ruleengine.model;

import java.time.Instant;

/**
 * Weighing registered by a scale of the balanzas node.
 *
 * @param weighingId id in the balanzas node (generated in memory: it resets when the node restarts)
 */
public record Weighing(long weighingId, long scaleId, String licensePlate, Instant timestamp, long weightKg)
        implements VehicleEvent {
}

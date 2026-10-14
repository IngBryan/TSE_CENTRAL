package tse.lab.fiscalizacionautomatica.ruleengine.model;

import java.time.Instant;

/**
 * Event of a vehicle reported by a peripheral node: what every fiscalization rule can rely on. A rule
 * that needs data of a specific node checks the type (e.g. {@code event instanceof Weighing w}).
 */
public sealed interface VehicleEvent permits TrackingEvent, Weighing {

    String licensePlate();

    Instant timestamp();
}

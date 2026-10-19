package tse.lab.fiscalizacionautomatica.ruleengine.detection.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.VehicleFacts;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.DatosViaje;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.DatosVehiculo;

class OverloadRuleTest {

    private static final Instant NOW = Instant.parse("2026-10-17T13:00:00Z");
    // 8500 kg + 25000 kg of capacity: a scale can show up to 33500 kg
    private static final DatosVehiculo VEHICLE = new DatosVehiculo("STU5678", 1L, 8500, 25000);
    private static final DatosViaje TRIP = new DatosViaje("GV-FISC001", "STU5678", 1L, "EN_CURSO",
            LocalDate.of(2026, 10, 17), NOW, "Mercedes", "Nueva Palmira", 20_000);
    private static final VehicleFacts WITH_TRIP = new VehicleFacts(VEHICLE, List.of(TRIP));

    private final OverloadRule rule = new OverloadRule();

    private static Weighing weighing(long weightKg) {
        return new Weighing(5, 4, "STU5678", NOW, weightKg);
    }

    @Test
    void appliesWhenTheWeighingIsHeavierThanTheVehiclePlusItsCapacity() {
        assertTrue(rule.appliesTo(weighing(33_501), WITH_TRIP));
    }

    @Test
    void doesNotApplyUpToTheVehiclePlusItsCapacity() {
        assertFalse(rule.appliesTo(weighing(33_500), WITH_TRIP));
    }

    @Test
    void onlyAppliesToWeighingsOfAVehicleWithAnActiveTrip() {
        assertFalse(rule.appliesTo(weighing(40_000), new VehicleFacts(VEHICLE, List.of())));
        assertFalse(rule.appliesTo(new TrackingEvent("msg-1", 7, 2, "STU5678", -34.9, -56.1, NOW), WITH_TRIP));
    }
}

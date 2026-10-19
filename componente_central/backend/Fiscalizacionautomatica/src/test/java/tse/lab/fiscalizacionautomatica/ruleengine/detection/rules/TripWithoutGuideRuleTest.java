package tse.lab.fiscalizacionautomatica.ruleengine.detection.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.VehicleFacts;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.DatosViaje;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.DatosVehiculo;

class TripWithoutGuideRuleTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:00Z");
    private static final DatosVehiculo VEHICLE = new DatosVehiculo("SBC1234", 1L, 8500, 25000);
    private static final TrackingEvent EVENT = new TrackingEvent("msg-1", 7, 1, "SBC1234", -34.9, -56.1, NOW);

    private final TripWithoutGuideRule rule = new TripWithoutGuideRule();

    @Test
    void appliesWhenTheVehicleHasNoAssignedOrOngoingTrip() {
        assertTrue(rule.appliesTo(EVENT, new VehicleFacts(VEHICLE, List.of())));
    }

    @ParameterizedTest
    @ValueSource(strings = { "ASIGNADA", "EN_CURSO" })
    void doesNotApplyWhenTheVehicleHasATrip(String state) {
        DatosViaje trip = new DatosViaje("GV-1", "SBC1234", 1L, state, LocalDate.of(2026, 10, 13), NOW,
                "Mercedes", "Nueva Palmira", 20_000);

        assertFalse(rule.appliesTo(EVENT, new VehicleFacts(VEHICLE, List.of(trip))));
    }
}

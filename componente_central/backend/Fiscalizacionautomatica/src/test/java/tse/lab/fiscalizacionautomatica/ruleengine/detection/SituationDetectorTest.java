package tse.lab.fiscalizacionautomatica.ruleengine.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.TripWithoutGuideRule;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.DatosViaje;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.DatosVehiculo;

class SituationDetectorTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:00Z");

    private final Map<String, VehicleFacts> central = new HashMap<>();
    private final VehicleFactsSource facts = licensePlate -> Optional.ofNullable(central.get(licensePlate));
    private final SituationDetector detector = new SituationDetector(facts, List.of(new TripWithoutGuideRule()));

    static VehicleFacts withoutTrips(String licensePlate) {
        return new VehicleFacts(new DatosVehiculo(licensePlate, 1L, 8500, 25000), List.of());
    }

    static VehicleFacts withTrip(String licensePlate, String state) {
        DatosViaje trip = new DatosViaje("GV-1", licensePlate, 1L, state, LocalDate.of(2026, 10, 13), NOW,
                "Mercedes", "Nueva Palmira", 20_000);
        return new VehicleFacts(new DatosVehiculo(licensePlate, 1L, 8500, 25000), List.of(trip));
    }

    private static TrackingEvent tracking(String licensePlate) {
        return new TrackingEvent("msg-1", 7, 1, licensePlate, -34.9, -56.1, NOW);
    }

    private static SituationRule alwaysApplies(Situation situation) {
        return new SituationRule() {
            @Override
            public Situation situation() {
                return situation;
            }

            @Override
            public boolean appliesTo(VehicleEvent event, VehicleFacts facts) {
                return true;
            }
        };
    }

    @Test
    void trackingEventOfAVehicleWithoutTripsIsATripWithoutGuide() throws IOException {
        central.put("SBC1234", withoutTrips("SBC1234"));

        assertEquals(List.of(Situation.TRIP_WITHOUT_GUIDE), detector.detect(tracking("SBC1234")).situations());
    }

    @Test
    void weighingOfAVehicleWithoutTripsIsATripWithoutGuideToo() throws IOException {
        central.put("SBC1234", withoutTrips("SBC1234"));

        assertEquals(List.of(Situation.TRIP_WITHOUT_GUIDE),
                detector.detect(new Weighing(3, 4, "SBC1234", NOW, 18_000)).situations());
    }

    @Test
    void vehicleWithAnActiveTripHasNoSituations() throws IOException {
        central.put("STU5678", withTrip("STU5678", "EN_CURSO"));

        assertEquals(List.of(), detector.detect(tracking("STU5678")).situations());
    }

    @Test
    void vehicleNotRegisteredInTheCentralHasNoSituations() throws IOException {
        Detection unknown = detector.detect(tracking("AAB9012"));
        assertEquals(List.of(), unknown.situations());
        assertEquals(null, unknown.facts());
    }

    @Test
    void situationsComeInPriorityOrderWhateverTheOrderOfTheRules() throws IOException {
        central.put("SBC1234", withoutTrips("SBC1234"));
        SituationDetector allRules = new SituationDetector(facts, List.of(alwaysApplies(Situation.ROUTE_DEVIATION),
                new TripWithoutGuideRule(), alwaysApplies(Situation.VEHICLE_WITHOUT_VALID_PERMIT)));

        assertEquals(List.of(Situation.TRIP_WITHOUT_GUIDE, Situation.VEHICLE_WITHOUT_VALID_PERMIT,
                Situation.ROUTE_DEVIATION), allRules.detect(tracking("SBC1234")).situations());
    }

    @Test
    void failureToEvaluateAnExternalRouteRuleIsReportedForRetry() {
        central.put("SBC1234", withTrip("SBC1234", "EN_CURSO"));
        SituationRule rule = new SituationRule() {
            @Override public Situation situation() { return Situation.ROUTE_DEVIATION; }
            @Override public boolean appliesTo(VehicleEvent event, VehicleFacts facts) throws IOException {
                throw new IOException("Consulta de rutas caida");
            }
        };
        SituationDetector detectorConFallo = new SituationDetector(facts, List.of(rule));
        assertThrows(IOException.class, () -> detectorConFallo.detect(tracking("SBC1234")));
    }

    @Test
    void failureToGetTheVehicleFactsIsReported() {
        SituationDetector wildFlyDown = new SituationDetector(licensePlate -> {
            throw new IOException("WildFly down");
        }, List.of(new TripWithoutGuideRule()));

        assertThrows(IOException.class, () -> wildFlyDown.detect(tracking("SBC1234")));
    }
}

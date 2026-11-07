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
import tse.lab.fiscalizacionautomatica.ruleengine.data.InMemoryTrackingEventDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.OverloadRule;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.RouteDeviationRule;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.TripWithoutGuideRule;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;
import tse.lab.fiscalizacionautomatica.ruleengine.trips.ContextoEventoSource;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.DatosViaje;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervaloGuiaGps;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.DatosVehiculo;
import tse.lab.frontofficeciudadanos.service.rutas.DatosRutaAutorizada;

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

    @Test
    void gpsAtrasadoConGuiaYaFinalizadaNoGeneraViajeSinGuia() throws Exception {
        central.put("SBC1234", withoutTrips("SBC1234"));
        var intervalo = new tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervaloGuiaGps(
                "G-ANT", "SBC1234", "FINALIZADA", NOW.minusSeconds(3600), NOW.minusSeconds(3000),
                NOW.plusSeconds(60));
        var ctx = new tse.lab.fiscalizacionautomatica.ruleengine.trips.ContextoEventoSource(
                (matricula, desde, hasta) -> List.of(intervalo));
        var detectorHistorico = new SituationDetector(facts, List.of(new TripWithoutGuideRule()), ctx);
        Detection detected = detectorHistorico.detect(tracking("SBC1234"));
        assertEquals(List.of(), detected.situations());
        assertEquals("G-ANT", detected.contexto().idGuia());
    }

    @Test
    void guiaInciertaNoMarcaEventoComoEvaluado() {
        central.put("SBC1234", withoutTrips("SBC1234"));
        var sinFin = new tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervaloGuiaGps(
                "G-ANT", "SBC1234", "FINALIZADA", NOW.minusSeconds(3600), NOW.minusSeconds(3000), null);
        var contexto = new tse.lab.fiscalizacionautomatica.ruleengine.trips.ContextoEventoSource(
                (matricula, desde, hasta) -> List.of(sinFin));
        var detectorHistorico = new SituationDetector(facts, List.of(new TripWithoutGuideRule()), contexto);
        assertThrows(IOException.class, () -> detectorHistorico.detect(tracking("SBC1234")));
    }

    private static IntervaloGuiaGps enCurso() {
        return new IntervaloGuiaGps("G1", "SBC1234", "EN_CURSO", NOW.minusSeconds(3600), NOW.minusSeconds(3000), null);
    }

    private SituationDetector conIntervalos(List<IntervaloGuiaGps> intervalos, SituationRule... reglas) {
        return new SituationDetector(facts, List.of(reglas), new ContextoEventoSource((m, d, h) -> intervalos));
    }

    @Test
    void gpsWithoutGuideInTheHistoryIsATripWithoutGuide() throws IOException {
        central.put("SBC1234", withTrip("SBC1234", "EN_CURSO"));

        Detection detected = conIntervalos(List.of(), new TripWithoutGuideRule()).detect(tracking("SBC1234"));

        assertEquals(List.of(Situation.TRIP_WITHOUT_GUIDE), detected.situations());
    }

    @Test
    void aWeighingWithoutGuideInTheHistoryIsNotATripWithoutGuide() throws IOException {
        central.put("SBC1234", withoutTrips("SBC1234"));

        Detection detected = conIntervalos(List.of(), new TripWithoutGuideRule())
                .detect(new Weighing(3, 4, "SBC1234", NOW, 18_390));

        assertEquals(List.of(), detected.situations());
    }

    @Test
    void overloadNeedsAGuideAtTheTimeOfTheWeighing() throws IOException {
        central.put("SBC1234", withoutTrips("SBC1234"));
        Weighing exceso = new Weighing(3, 4, "SBC1234", NOW, 99_000);

        assertEquals(List.of(Situation.OVERLOAD),
                conIntervalos(List.of(enCurso()), new OverloadRule()).detect(exceso).situations());
        assertEquals(List.of(), conIntervalos(List.of(), new OverloadRule()).detect(exceso).situations());
    }

    @Test
    void anAmbiguousAssociationIsRetried() {
        central.put("SBC1234", withoutTrips("SBC1234"));
        IntervaloGuiaGps otra = new IntervaloGuiaGps("G2", "SBC1234", "EN_CURSO", NOW.minusSeconds(3600),
                NOW.minusSeconds(3000), null);

        assertThrows(IOException.class, () -> conIntervalos(List.of(enCurso(), otra), new TripWithoutGuideRule())
                .detect(tracking("SBC1234")));
    }

    @Test
    void aConfirmedDeviationKeepsItsProof() throws IOException {
        central.put("SBC1234", withoutTrips("SBC1234"));
        InMemoryTrackingEventDAO dao = new InMemoryTrackingEventDAO();
        dao.save(new TrackingEvent("msg-0", 6, 1, "SBC1234", -34.25, -55.95, NOW.minusSeconds(10)));
        DatosRutaAutorizada ruta = new DatosRutaAutorizada(1L, 1, "sha", 1000,
                "{\"type\":\"LineString\",\"coordinates\":[[-56,-34.5],[-56,-34]]}");
        RouteDeviationRule regla = new RouteDeviationRule((m, d, h) -> List.of(enCurso()), id -> List.of(ruta), dao);
        TrackingEvent fuera = new TrackingEvent("msg-1", 7, 1, "SBC1234", -34.25, -55.951, NOW);

        Detection detected = conIntervalos(List.of(enCurso()), regla).detect(fuera);

        assertEquals(List.of(Situation.ROUTE_DEVIATION), detected.situations());
        assertEquals(fuera, detected.pruebaDesvio().confirmante());
    }
}

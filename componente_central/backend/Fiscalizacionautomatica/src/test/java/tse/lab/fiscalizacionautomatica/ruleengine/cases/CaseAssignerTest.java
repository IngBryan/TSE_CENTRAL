package tse.lab.fiscalizacionautomatica.ruleengine.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.Detection;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.Situation;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.VehicleFacts;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;
import tse.lab.frontofficefuncionarios.service.gestioncasos.DatosCaso;
import tse.lab.frontofficefuncionarios.service.gestioncasos.TipoCasoAutomatico;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.DatosViaje;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.DatosVehiculo;

class CaseAssignerTest {

    private static final Instant T1 = Instant.parse("2026-10-17T13:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-17T13:00:10Z");
    private static final Instant T3 = Instant.parse("2026-10-17T13:00:20Z");
    private static final DatosVehiculo VEHICLE = new DatosVehiculo("SBC1234", 1L, 8500, 25000);
    private static final VehicleFacts WITHOUT_TRIP = new VehicleFacts(VEHICLE, List.of());

    /** In memory, with the same guarantees as the database. */
    static class FakeCaseStore implements CaseStore {

        record StoredCase(long id, String type, String guideId, List<VehicleEvent> evidence, String[] state) {
        }

        final List<StoredCase> cases = new ArrayList<>();
        final Map<VehicleEvent, Long> caseOfEvent = new HashMap<>();
        boolean down;

        @Override
        public List<DatosCaso> openCases(String licensePlate) throws IOException {
            failIfDown();
            return cases.stream()
                    .filter(c -> c.state()[0].equals("DETECTADA") || c.state()[0].equals("NOTIFICADA"))
                    .map(FakeCaseStore::data)
                    .toList();
        }

        @Override
        public DatosCaso caseOf(VehicleEvent event) throws IOException {
            failIfDown();
            Long id = caseOfEvent.get(event);
            return id == null ? null : data(find(id));
        }

        @Override
        public DatosCaso create(TipoCasoAutomatico caseType, String guideId, VehicleEvent event) throws IOException {
            failIfDown();
            StoredCase created = new StoredCase(cases.size() + 1L, caseType.name(), guideId, new ArrayList<>(),
                    new String[] { "DETECTADA" });
            cases.add(created);
            addEvidence(created.id(), event);
            return data(created);
        }

        @Override
        public void addEvidence(long caseId, VehicleEvent event) throws IOException {
            failIfDown();
            find(caseId).evidence().add(event);
            caseOfEvent.put(event, caseId);
        }

        @Override
        public DatosCaso notify(long caseId) throws IOException {
            failIfDown();
            StoredCase stored = find(caseId);
            if (stored.state()[0].equals("DETECTADA")) {
                stored.state()[0] = "NOTIFICADA";
            }
            return data(stored);
        }

        StoredCase find(long id) {
            return cases.get((int) id - 1);
        }

        private void failIfDown() throws IOException {
            if (down) {
                throw new IOException("WildFly down");
            }
        }

        private static DatosCaso data(StoredCase c) {
            return new DatosCaso(c.id(), c.type(), c.state()[0]);
        }
    }

    private final FakeCaseStore store = new FakeCaseStore();
    private final CaseAssigner assigner = new CaseAssigner(store);

    private static TrackingEvent tracking(Instant timestamp) {
        return new TrackingEvent("msg", 7, 1, "SBC1234", -34.9, -56.1, timestamp);
    }

    private static Detection detection(VehicleEvent event, VehicleFacts facts, Situation... situations) {
        return new Detection(event, facts, List.of(situations));
    }

    @Test
    void createsTheCaseAndNotifiesIt() {
        assigner.accept(detection(tracking(T1), WITHOUT_TRIP, Situation.TRIP_WITHOUT_GUIDE));

        assertEquals(1, store.cases.size());
        FakeCaseStore.StoredCase created = store.cases.get(0);
        assertEquals("VIAJE_SIN_GUIA", created.type());
        assertEquals("NOTIFICADA", created.state()[0]);
        assertEquals(null, created.guideId());
        assertEquals(List.of(tracking(T1)), created.evidence());
    }

    @Test
    void anotherEventOfTheSameSituationIsAddedToTheOpenCase() {
        assigner.accept(detection(tracking(T1), WITHOUT_TRIP, Situation.TRIP_WITHOUT_GUIDE));

        assigner.accept(detection(tracking(T2), WITHOUT_TRIP, Situation.TRIP_WITHOUT_GUIDE));

        assertEquals(1, store.cases.size());
        assertEquals(List.of(tracking(T1), tracking(T2)), store.cases.get(0).evidence());
    }

    @Test
    void aCaseInDescargoIsNotOpenAnymoreSoANewOneIsCreated() {
        assigner.accept(detection(tracking(T1), WITHOUT_TRIP, Situation.TRIP_WITHOUT_GUIDE));
        store.cases.get(0).state()[0] = "EN_DESCARGO";

        assigner.accept(detection(tracking(T2), WITHOUT_TRIP, Situation.TRIP_WITHOUT_GUIDE));

        assertEquals(2, store.cases.size());
    }

    @Test
    void withTheHighestSituationOpenTheNextOneIsCreatedAndThenEventsGoToTheHighest() {
        assigner.accept(detection(tracking(T1), WITHOUT_TRIP, Situation.TRIP_WITHOUT_GUIDE));

        assigner.accept(detection(tracking(T2), WITHOUT_TRIP, Situation.TRIP_WITHOUT_GUIDE,
                Situation.VEHICLE_WITHOUT_VALID_PERMIT));
        assertEquals(2, store.cases.size());
        assertEquals("VEHICULO_SIN_HABILITACION", store.cases.get(1).type());

        assigner.accept(detection(tracking(T3), WITHOUT_TRIP, Situation.TRIP_WITHOUT_GUIDE,
                Situation.VEHICLE_WITHOUT_VALID_PERMIT));
        assertEquals(2, store.cases.size());
        assertEquals(List.of(tracking(T1), tracking(T3)), store.cases.get(0).evidence());
    }

    @Test
    void anEventThatIsAlreadyEvidenceIsNotAssignedAgain() {
        assigner.accept(detection(tracking(T1), WITHOUT_TRIP, Situation.TRIP_WITHOUT_GUIDE));

        assigner.accept(detection(tracking(T1), WITHOUT_TRIP, Situation.TRIP_WITHOUT_GUIDE,
                Situation.VEHICLE_WITHOUT_VALID_PERMIT));

        assertEquals(1, store.cases.size());
        assertEquals(1, store.cases.get(0).evidence().size());
    }

    @Test
    void anOverloadCaseHasTheGuideOfTheActiveTrip() {
        DatosViaje trip = new DatosViaje("GV-FISC001", "SBC1234", 1L, "EN_CURSO", LocalDate.of(2026, 10, 17), T1,
                "Mercedes", "Nueva Palmira", 20_000);

        assigner.accept(detection(new Weighing(3, 4, "SBC1234", T1, 40_000), new VehicleFacts(VEHICLE, List.of(trip)),
                Situation.OVERLOAD));

        assertEquals("EXCESO_CARGA", store.cases.get(0).type());
        assertEquals("GV-FISC001", store.cases.get(0).guideId());
    }

    @Test
    void aCaseLeftDetectedIsNotifiedWithItsNextEvent() {
        assigner.accept(detection(tracking(T1), WITHOUT_TRIP, Situation.TRIP_WITHOUT_GUIDE));
        store.cases.get(0).state()[0] = "DETECTADA"; // as if the notification had failed

        assigner.accept(detection(tracking(T2), WITHOUT_TRIP, Situation.TRIP_WITHOUT_GUIDE));

        assertEquals("NOTIFICADA", store.cases.get(0).state()[0]);
    }

    @Test
    void anEventWithoutSituationsDoesNothing() {
        assigner.accept(detection(tracking(T1), null));

        assertEquals(0, store.cases.size());
    }

    @Test
    void aFailedCallThrowsSoTheEventStaysPending() {
        store.down = true;

        assertThrows(UncheckedIOException.class,
                () -> assigner.accept(detection(tracking(T1), WITHOUT_TRIP, Situation.TRIP_WITHOUT_GUIDE)));
    }
}

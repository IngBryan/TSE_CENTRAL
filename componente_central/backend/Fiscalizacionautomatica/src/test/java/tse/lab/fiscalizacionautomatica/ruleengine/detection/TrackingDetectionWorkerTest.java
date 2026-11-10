package tse.lab.fiscalizacionautomatica.ruleengine.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tse.lab.fiscalizacionautomatica.ruleengine.data.InMemoryTrackingEventDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.data.TrackingEventDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.TripWithoutGuideRule;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.polling.MutableClock;
import tse.lab.fiscalizacionautomatica.ruleengine.reception.TrackingEventReceiver;

class TrackingDetectionWorkerTest {

    private static final Instant NOW = Instant.parse("2026-10-13T15:00:00Z");
    private static final Duration PENDING_FOR = Duration.ofMinutes(1);

    record Result(VehicleEvent event, List<Situation> situations) {
    }

    private final MutableClock clock = new MutableClock(NOW);
    private final InMemoryTrackingEventDAO dao = new InMemoryTrackingEventDAO(clock);
    private final Map<String, VehicleFacts> central = new HashMap<>();
    private volatile boolean wildFlyDown;
    private final List<Result> detections = new CopyOnWriteArrayList<>();

    private final VehicleFactsSource facts = licensePlate -> {
        if (wildFlyDown) {
            throw new IOException("WildFly down");
        }
        return Optional.ofNullable(central.get(licensePlate));
    };
    private final TrackingDetectionWorker worker = new TrackingDetectionWorker(
            new SituationDetector(facts, List.of(new TripWithoutGuideRule())), dao,
            d -> detections.add(new Result(d.event(), d.situations())), clock, 2, 100);

    @AfterEach
    void cerrarWorker() {
        worker.close();
    }

    private TrackingEvent saved(String licensePlate) {
        TrackingEvent event = new TrackingEvent("msg-1", 7, 1, licensePlate, -34.9, -56.1, clock.instant());
        dao.save(event);
        return event;
    }

    @Test
    void evaluatesASubmittedEventAndMarksItAsDetected() {
        central.put("SBC1234", SituationDetectorTest.withoutTrips("SBC1234"));
        TrackingEvent event = saved("SBC1234");

        worker.submit(event);
        worker.close();

        assertEquals(List.of(new Result(event, List.of(Situation.TRIP_WITHOUT_GUIDE))), detections);
        assertTrue(dao.isDetected(event));
    }

    @Test
    void anEventWithoutSituationsIsMarkedAsDetectedToo() {
        central.put("STU5678", SituationDetectorTest.withTrip("STU5678", "EN_CURSO"));
        TrackingEvent event = saved("STU5678");

        worker.submit(event);
        worker.close();

        assertEquals(List.of(new Result(event, List.of())), detections);
        assertTrue(dao.isDetected(event));
    }

    @Test
    void ifWildFlyIsDownTheEventStaysPending() {
        wildFlyDown = true;
        TrackingEvent event = saved("SBC1234");

        worker.submit(event);
        worker.close();

        assertEquals(List.of(), detections);
        assertFalse(dao.isDetected(event));
    }

    @Test
    void recoveryQueuesAgainTheEventsPendingForTooLong() {
        // Saved but never evaluated: e.g. the pod restarted, or WildFly was down
        central.put("SBC1234", SituationDetectorTest.withoutTrips("SBC1234"));
        TrackingEvent event = saved("SBC1234");

        clock.advance(Duration.ofMinutes(2));
        worker.recoverPending(PENDING_FOR);
        worker.close();

        assertEquals(List.of(new Result(event, List.of(Situation.TRIP_WITHOUT_GUIDE))), detections);
        assertTrue(dao.isDetected(event));
    }

    @Test
    void recoveryLeavesRecentPendingEventsAlone() {
        central.put("SBC1234", SituationDetectorTest.withoutTrips("SBC1234"));
        TrackingEvent event = saved("SBC1234");

        clock.advance(Duration.ofSeconds(30));
        worker.recoverPending(PENDING_FOR);
        worker.close();

        assertEquals(List.of(), detections);
        assertFalse(dao.isDetected(event));
    }

    @Test
    void guardadoAntesDeFallarElDespachoSeRecuperaAunqueLaReentregaSeaDuplicada() {
        central.put("SBC1234", SituationDetectorTest.withoutTrips("SBC1234"));
        byte[] body = """
                {"id":7,"gpsId":1,"licensePlate":"SBC1234","latitude":-34.9,
                 "longitude":-56.1,"datetime":"2026-10-13T15:00:00Z"}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var receptor = new TrackingEventReceiver(dao, gps -> true, e -> {
            throw new IllegalStateException("Falla entre guardar y encolar");
        });
        assertThrows(IllegalStateException.class, () -> receptor.receive("msg-original", body));
        var guardado = dao.events().getFirst();
        assertFalse(dao.isDetected(guardado));
        var cola = new CopyOnWriteArrayList<TrackingEvent>();
        var reintento = new TrackingEventReceiver(dao, gps -> true, cola::add);
        assertEquals(TrackingEventReceiver.Result.DUPLICATE, reintento.receive("msg-reentrega", body));
        assertTrue(cola.isEmpty());

        clock.advance(Duration.ofMinutes(2));
        worker.recoverPending(PENDING_FOR);
        worker.close();

        assertEquals(List.of(new Result(guardado, List.of(Situation.TRIP_WITHOUT_GUIDE))), detections);
        assertTrue(dao.isDetected(guardado));
        assertEquals(1, dao.events().size());
        assertEquals("msg-original", dao.events().getFirst().messageId());
    }

    @ParameterizedTest(name = "TR-F02: falla en {0}, recupera otro worker")
    @ValueSource(strings = { "ASIGNACION", "MARCA_FINAL" })
    void falloDeProcesamientoSeReintentaSinPerderLaIdentidad(String etapa) {
        central.put("SBC1234", SituationDetectorTest.withoutTrips("SBC1234"));
        var evento = saved("SBC1234");
        var persistencia = spy(dao);
        var intentos = new CopyOnWriteArrayList<TrackingEvent>();
        var primerIntento = new AtomicBoolean(true);
        Consumer<Detection> asignacion = d -> {
            intentos.add((TrackingEvent) d.event());
            if (etapa.equals("ASIGNACION") && primerIntento.getAndSet(false)) {
                throw new IllegalStateException("Falla al asignar casos");
            }
        };
        if (etapa.equals("MARCA_FINAL")) {
            doThrow(new IllegalStateException("Falla antes de marcar detectado"))
                    .doCallRealMethod().when(persistencia).markDetected(evento);
        }
        try (var primero = nuevoWorker(persistencia, asignacion, 10)) {
            primero.detect(evento);
        }
        assertFalse(dao.isDetected(evento));
        assertEquals(List.of(evento), intentos);

        clock.advance(Duration.ofMinutes(2));
        try (var reiniciado = nuevoWorker(persistencia, asignacion, 10)) {
            reiniciado.recoverPending(PENDING_FOR);
        }
        assertTrue(dao.isDetected(evento));
        assertEquals(List.of(evento, evento), intentos);
        assertEquals(List.of(evento), dao.events());
    }

    @Test
    void eventoEnCursoNoSeEncolaOtraVezPorEntregaNiPorRecuperacion() throws Exception {
        central.put("SBC1234", SituationDetectorTest.withoutTrips("SBC1234"));
        var evento = saved("SBC1234");
        var entro = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var evaluados = new CopyOnWriteArrayList<TrackingEvent>();
        var bloqueado = nuevoWorker(dao, d -> {
            evaluados.add((TrackingEvent) d.event());
            entro.countDown();
            esperar(liberar);
        }, 10);
        try {
            bloqueado.submit(evento);
            assertTrue(entro.await(5, TimeUnit.SECONDS));
            bloqueado.submit(new TrackingEvent("otra-entrega", 99, 1, evento.licensePlate(),
                    evento.latitude(), evento.longitude(), evento.timestamp()));
            clock.advance(Duration.ofMinutes(2));
            bloqueado.recoverPending(PENDING_FOR);
            assertEquals(List.of(evento), evaluados);
        } finally {
            liberar.countDown();
            bloqueado.close();
        }
        assertEquals(List.of(evento), evaluados);
        assertTrue(dao.isDetected(evento));
    }

    @Test
    void colaLlenaDejaElEventoPendienteYLaRecuperacionLoProcesa() throws Exception {
        central.put("SBC1234", SituationDetectorTest.withoutTrips("SBC1234"));
        var primero = saved("SBC1234");
        var segundo = new TrackingEvent("msg-2", 8, 1, "SBC1234", -34.9, -56.1, NOW.plusSeconds(10));
        var tercero = new TrackingEvent("msg-3", 9, 1, "SBC1234", -34.9, -56.1, NOW.plusSeconds(20));
        dao.saveAll(List.of(segundo, tercero));
        var entro = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var evaluados = new CopyOnWriteArrayList<TrackingEvent>();
        var saturado = nuevoWorker(dao, d -> {
            evaluados.add((TrackingEvent) d.event());
            if (d.event().equals(primero)) {
                entro.countDown();
                esperar(liberar);
            }
        }, 1);
        try {
            saturado.submit(primero);
            assertTrue(entro.await(5, TimeUnit.SECONDS));
            saturado.submit(segundo);
            saturado.submit(tercero);
        } finally {
            liberar.countDown();
            saturado.close();
        }
        assertEquals(List.of(primero, segundo), evaluados);
        assertFalse(dao.isDetected(tercero));
        clock.advance(Duration.ofMinutes(2));
        try (var recuperado = nuevoWorker(dao, d -> evaluados.add((TrackingEvent) d.event()), 10)) {
            recuperado.recoverPending(PENDING_FOR);
        }
        assertEquals(List.of(primero, segundo, tercero), evaluados);
        assertTrue(dao.isDetected(tercero));
    }

    @Test
    void falloAlConsultarPendientesNoImpideLaSiguienteRecuperacion() {
        central.put("SBC1234", SituationDetectorTest.withoutTrips("SBC1234"));
        var evento = saved("SBC1234");
        clock.advance(Duration.ofMinutes(2));
        var persistencia = spy(dao);
        doThrow(new IllegalStateException("Almacenamiento indisponible")).doCallRealMethod()
                .when(persistencia).findPendingDetection(clock.instant().minus(PENDING_FOR), 500);
        var evaluados = new CopyOnWriteArrayList<TrackingEvent>();
        try (var recuperado = nuevoWorker(persistencia, d -> evaluados.add((TrackingEvent) d.event()), 10)) {
            recuperado.recoverPending(PENDING_FOR);
            assertTrue(evaluados.isEmpty());
            assertFalse(dao.isDetected(evento));
            recuperado.recoverPending(PENDING_FOR);
        }
        assertEquals(List.of(evento), evaluados);
        assertTrue(dao.isDetected(evento));
    }

    private TrackingDetectionWorker nuevoWorker(TrackingEventDAO persistencia, Consumer<Detection> asignacion,
            int capacidad) {
        return new TrackingDetectionWorker(new SituationDetector(facts, List.of(new TripWithoutGuideRule())),
                persistencia, asignacion, clock, 1, capacidad);
    }

    private static void esperar(CountDownLatch liberar) {
        try {
            if (!liberar.await(5, TimeUnit.SECONDS)) throw new AssertionError("No se liberó el worker");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}

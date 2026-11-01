package tse.lab.fiscalizacionautomatica.ruleengine.cases;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.Detection;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.Situation;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.RouteDeviationRule.PruebaDesvio;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.trips.ResultadoAsociacionGpsGuia;
import tse.lab.frontofficefuncionarios.service.gestioncasos.DatosCaso;
import tse.lab.frontofficefuncionarios.service.gestioncasos.TipoCasoAutomatico;

class CaseAssignerTest {
    static class FakeStore implements CaseStore {
        record Caso(long id, TipoCasoAutomatico tipo, String guia, List<VehicleEvent> evidencias, String[] estado) { }
        final List<Caso> casos = new ArrayList<>();
        TipoCasoAutomatico fallarTipo;

        @Override
        public DatosCaso registrar(TipoCasoAutomatico tipo, String guia, VehicleEvent evento) throws IOException {
            if (tipo == fallarTipo) throw new IOException("Falló segundo caso");
            Caso caso = casos.stream().filter(c -> c.tipo() == tipo && java.util.Objects.equals(c.guia(), guia)
                    && c.evidencias().contains(evento)).findFirst().orElse(null);
            if (caso != null) return data(caso); // reentrega, también tras el cierre
            caso = casos.stream().filter(c -> c.tipo() == tipo && java.util.Objects.equals(c.guia(), guia)
                    && (c.estado()[0].equals("DETECTADA") || c.estado()[0].equals("NOTIFICADA")))
                    .findFirst().orElse(null);
            if (caso == null) {
                caso = new Caso(casos.size() + 1L, tipo, guia, new ArrayList<>(), new String[]{"DETECTADA"});
                casos.add(caso);
            }
            if (!caso.evidencias().contains(evento)) caso.evidencias().add(evento);
            return data(caso);
        }
        @Override
        public DatosCaso registrarCirculacionSinGuia(TrackingEvent evento) throws IOException {
            // Fake conserva el contrato antiguo para probar el routing; el ciclo de vida
            // y la confirmación de movimiento se prueban en CasosAutomaticosServiceTest.
            return registrar(TipoCasoAutomatico.VIAJE_SIN_GUIA, null, evento);
        }
        @Override
        public DatosCaso registrarDesvio(String guia, TrackingEvent evento, PruebaDesvio prueba) throws IOException {
            if (prueba == null) throw new IOException("Prueba faltante");
            return registrar(TipoCasoAutomatico.DESVIO_RUTA, guia, evento);
        }
        @Override
        public DatosCaso notify(long id) {
            Caso c = casos.get((int)id - 1);
            c.estado()[0] = "NOTIFICADA";
            return data(c);
        }
        private static DatosCaso data(Caso c) { return new DatosCaso(c.id(), c.tipo().name(), c.estado()[0]); }
    }

    private final FakeStore store = new FakeStore();
    private final CaseAssigner asignador = new CaseAssigner(store);
    private static TrackingEvent gps(int segundos) {
        return new TrackingEvent("msg", 7, 1, "SBC1234", -34.9, -56.1,
                Instant.parse("2026-10-17T13:00:00Z").plusSeconds(segundos));
    }
    private static Detection conGuia(VehicleEvent e, String guia, Situation... tipos) {
        if (List.of(tipos).contains(Situation.ROUTE_DEVIATION)) {
            TrackingEvent gps = (TrackingEvent) e;
            PruebaDesvio prueba = new PruebaDesvio(gps, gps((int) (gps.timestamp().getEpochSecond()
                    - gps(0).timestamp().getEpochSecond()) - 10), 2300, 2500, 2000, 30, List.of("sha-a"));
            return new Detection(e, null, List.of(tipos), ResultadoAsociacionGpsGuia.asociado(guia), prueba);
        }
        return new Detection(e, null, List.of(tipos), ResultadoAsociacionGpsGuia.asociado(guia));
    }
    private static Detection sinGuia(VehicleEvent e, Situation... tipos) {
        return new Detection(e, null, List.of(tipos), ResultadoAsociacionGpsGuia.sinGuia());
    }

    @Test
    void eventoPuedeRespaldarDistintasInfracciones() {
        asignador.accept(sinGuia(gps(0), Situation.TRIP_WITHOUT_GUIDE,
                Situation.VEHICLE_WITHOUT_VALID_PERMIT));
        assertEquals(2, store.casos.size());
        assertEquals(List.of(gps(0)), store.casos.get(0).evidencias());
        assertEquals(List.of(gps(0)), store.casos.get(1).evidencias());
        assertEquals("NOTIFICADA", store.casos.get(1).estado()[0]);
    }

    @Test
    void reentregaNoDuplicaNingunaEvidencia() {
        var d = sinGuia(gps(0), Situation.TRIP_WITHOUT_GUIDE, Situation.VEHICLE_WITHOUT_VALID_PERMIT);
        asignador.accept(d);
        asignador.accept(d);
        assertEquals(2, store.casos.size());
        assertTrue(store.casos.stream().allMatch(c -> c.evidencias().size() == 1));
    }

    @Test
    void distintasGuiasMismoCamionNoCompartenCasoAbierto() {
        asignador.accept(conGuia(gps(0), "G-A", Situation.ROUTE_DEVIATION));
        asignador.accept(conGuia(gps(10), "G-B", Situation.ROUTE_DEVIATION));
        asignador.accept(conGuia(gps(20), "G-A", Situation.ROUTE_DEVIATION));
        assertEquals(2, store.casos.size());
        assertEquals(List.of(gps(0), gps(20)), store.casos.get(0).evidencias());
        assertEquals("G-B", store.casos.get(1).guia());
    }

    @Test
    void fakeDeAsignacionConservaCasoAbiertoSinSepararCirculaciones() {
        asignador.accept(sinGuia(gps(0), Situation.TRIP_WITHOUT_GUIDE));
        asignador.accept(sinGuia(gps(86400), Situation.TRIP_WITHOUT_GUIDE));
        assertEquals(1, store.casos.size());
        assertEquals(2, store.casos.get(0).evidencias().size());
    }

    @Test
    void eventoReprocesadoTrasCerrarCasoNoAbreOtro() {
        var d1 = sinGuia(gps(0), Situation.TRIP_WITHOUT_GUIDE);
        asignador.accept(d1);
        store.casos.get(0).estado()[0] = "EN_DESCARGO";
        asignador.accept(d1); // redelivery del mismo GPS
        assertEquals(1, store.casos.size());
        asignador.accept(sinGuia(gps(10), Situation.TRIP_WITHOUT_GUIDE));
        assertEquals(2, store.casos.size()); // evidencia nueva después del cierre
    }

    @Test
    void reintentoTrasFalloParcialCompletaSituacionPendiente() {
        var d = sinGuia(gps(0), Situation.TRIP_WITHOUT_GUIDE, Situation.VEHICLE_WITHOUT_VALID_PERMIT);
        store.fallarTipo = TipoCasoAutomatico.VEHICULO_SIN_HABILITACION;
        assertThrows(UncheckedIOException.class, () -> asignador.accept(d));
        assertEquals(1, store.casos.size());
        store.fallarTipo = null;
        asignador.accept(d);
        assertEquals(2, store.casos.size());
        assertEquals(1, store.casos.get(0).evidencias().size());
    }

    @Test
    void noAsignaConContextoInciertoNiInexistente() {
        assertThrows(UncheckedIOException.class, () -> asignador.accept(
                new Detection(gps(0), null, List.of(Situation.TRIP_WITHOUT_GUIDE))));
        assertThrows(UncheckedIOException.class, () -> asignador.accept(new Detection(gps(0), null,
                List.of(Situation.ROUTE_DEVIATION), ResultadoAsociacionGpsGuia.ambiguo())));
        assertEquals(0, store.casos.size());
    }

    @Test
    void noGeneraDesvioSinGuia() {
        assertThrows(UncheckedIOException.class,
                () -> asignador.accept(sinGuia(gps(0), Situation.ROUTE_DEVIATION)));
    }
}

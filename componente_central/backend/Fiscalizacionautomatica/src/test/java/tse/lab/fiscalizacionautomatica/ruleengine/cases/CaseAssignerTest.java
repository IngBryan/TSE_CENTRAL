package tse.lab.fiscalizacionautomatica.ruleengine.cases;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.Detection;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.Situation;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.VehicleFacts;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.RouteDeviationRule.PruebaDesvio;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;
import tse.lab.fiscalizacionautomatica.ruleengine.trips.ResultadoAsociacionGpsGuia;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.DatosVehiculo;
import tse.lab.frontofficefuncionarios.service.gestioncasos.DatosCaso;
import tse.lab.frontofficefuncionarios.service.gestioncasos.TipoCasoAutomatico;
import tse.lab.model.EvidenciaHabilitacion;

// Orquestación: no reimplementa la asignación ni la idempotencia del servicio en un FakeStore.
class CaseAssignerTest {
    private final CaseStore store = mock(CaseStore.class);
    private final CaseAssigner asignador = new CaseAssigner(store);
    private static final TrackingEvent GPS = new TrackingEvent("msg-1", 7, 1, "SBC1234", -34.9, -56.1,
            Instant.parse("2026-10-17T13:00:00Z"));

    private static Detection sinGuia(VehicleEvent evento, Situation... tipos) {
        var prueba = new EvidenciaHabilitacion(evento.licensePlate(), evento.timestamp(), null, null, null, null, null);
        var hechos = new VehicleFacts(new DatosVehiculo(evento.licensePlate(), 1L, 8500, 25000, prueba), List.of());
        return new Detection(evento, hechos, List.of(tipos), ResultadoAsociacionGpsGuia.sinGuia());
    }

    private static DatosCaso caso(long id, TipoCasoAutomatico tipo, String estado) {
        return new DatosCaso(id, tipo.name(), estado);
    }

    @Test
    void eventoPuedeRespaldarDistintasInfracciones() throws Exception {
        var d = sinGuia(GPS, Situation.TRIP_WITHOUT_GUIDE, Situation.VEHICLE_WITHOUT_VALID_PERMIT);
        when(store.registrarCirculacionSinGuia(GPS)).thenReturn(caso(1, TipoCasoAutomatico.VIAJE_SIN_GUIA, "DETECTADA"));
        when(store.registrarHabilitacion(null, GPS, d.facts().vehicle().getHabilitacion()))
                .thenReturn(caso(2, TipoCasoAutomatico.VEHICULO_SIN_HABILITACION, "DETECTADA"));
        asignador.accept(d);
        var orden = inOrder(store);
        orden.verify(store).registrarCirculacionSinGuia(GPS);
        orden.verify(store).notify(1, GPS.correlationId());
        orden.verify(store).registrarHabilitacion(null, GPS, d.facts().vehicle().getHabilitacion());
        orden.verify(store).notify(2, GPS.correlationId());
        orden.verifyNoMoreInteractions();
    }

    @ParameterizedTest(name = "TR-N01: caso devuelto en {0}")
    @CsvSource({ "DETECTADA, true", "NOTIFICADA, false", "EN_DESCARGO, false", "CONFIRMADA, false", "DESESTIMADA, false" })
    void soloNotificaCasosDetectados(String estado, boolean notifica) throws Exception {
        when(store.registrarCirculacionSinGuia(GPS)).thenReturn(caso(1, TipoCasoAutomatico.VIAJE_SIN_GUIA, estado));
        asignador.accept(sinGuia(GPS, Situation.TRIP_WITHOUT_GUIDE));
        verify(store).registrarCirculacionSinGuia(GPS);
        verify(store, times(notifica ? 1 : 0)).notify(1, GPS.correlationId());
        verifyNoMoreInteractions(store);
    }

    @Test
    void reintentoTrasFalloParcialCompletaSituacionPendiente() throws Exception {
        var d = sinGuia(GPS, Situation.TRIP_WITHOUT_GUIDE, Situation.VEHICLE_WITHOUT_VALID_PERMIT);
        when(store.registrarCirculacionSinGuia(GPS)).thenReturn(
                caso(1, TipoCasoAutomatico.VIAJE_SIN_GUIA, "DETECTADA"), caso(1, TipoCasoAutomatico.VIAJE_SIN_GUIA, "NOTIFICADA"));
        when(store.registrarHabilitacion(null, GPS, d.facts().vehicle().getHabilitacion()))
                .thenThrow(new IOException("Falla la segunda infracción"))
                .thenReturn(caso(2, TipoCasoAutomatico.VEHICULO_SIN_HABILITACION, "DETECTADA"));
        assertThrows(UncheckedIOException.class, () -> asignador.accept(d));
        verify(store).notify(1, GPS.correlationId());
        verify(store, never()).notify(eq(2L), any());
        asignador.accept(d);
        verify(store, times(2)).registrarCirculacionSinGuia(GPS);
        verify(store, times(2)).registrarHabilitacion(null, GPS, d.facts().vehicle().getHabilitacion());
        verify(store, times(1)).notify(1, GPS.correlationId());
        verify(store, times(1)).notify(2, GPS.correlationId());
    }

    @Test
    void falloDeNotificacionSePropagaParaReintentar() throws Exception {
        when(store.registrarCirculacionSinGuia(GPS)).thenReturn(caso(1, TipoCasoAutomatico.VIAJE_SIN_GUIA, "DETECTADA"));
        when(store.notify(1, GPS.correlationId())).thenThrow(new IOException("Notificación indisponible"));
        assertThrows(UncheckedIOException.class, () -> asignador.accept(sinGuia(GPS, Situation.TRIP_WITHOUT_GUIDE)));
    }

    @Test
    void distintasGuiasSeDeleganConSuContextoYPruebaGeometrica() throws Exception {
        var pareja = new TrackingEvent("msg-0", 6, 1, "SBC1234", -34.9, -56.12, GPS.timestamp().minusSeconds(10));
        var prueba = new PruebaDesvio(GPS, pareja, 2300, 2500, 2000, 30, List.of("sha-a"));
        var config = tse.lab.reglas.ConfiguracionReglas.inicial();
        for (String guia : List.of("G-A", "G-B")) {
            asignador.accept(new Detection(GPS, null, List.of(Situation.ROUTE_DEVIATION),
                    ResultadoAsociacionGpsGuia.asociado(guia), prueba, config));
            verify(store).cerrarCirculacionPorGuia(GPS.licensePlate(), GPS.timestamp());
            verify(store).registrarDesvioConReglas(guia, GPS, prueba, config);
            clearInvocations(store);
        }
    }

    @Test
    void noAsignaConContextoInciertoNiInexistente() {
        for (var contexto : new ResultadoAsociacionGpsGuia[] { null,
                ResultadoAsociacionGpsGuia.ambiguo(), ResultadoAsociacionGpsGuia.noDeterminable() }) {
            assertThrows(UncheckedIOException.class, () -> asignador.accept(
                    new Detection(GPS, null, List.of(Situation.TRIP_WITHOUT_GUIDE), contexto)));
        }
        verifyNoInteractions(store);
    }

    @Test
    void noGeneraDesvioSinGuia() {
        assertThrows(UncheckedIOException.class, () -> asignador.accept(sinGuia(GPS, Situation.ROUTE_DEVIATION)));
        verifyNoInteractions(store);
    }

    @Test
    void laEvidenciaDeCargaConservaLaRevisionYElPesoBaseSinDesbordar() throws Exception {
        var evento = new Weighing(1, 1, "SBC1234", GPS.timestamp(), 50000);
        var vehiculo = new DatosVehiculo("SBC1234", 1L, Integer.MAX_VALUE, Integer.MAX_VALUE, null);
        var config = tse.lab.reglas.ConfiguracionReglas.inicial();
        asignador.accept(new Detection(evento, new VehicleFacts(vehiculo, List.of()), List.of(Situation.OVERLOAD),
                ResultadoAsociacionGpsGuia.asociado("G1"), null, config));
        verify(store).registrarConReglas(TipoCasoAutomatico.EXCESO_CARGA, "G1", evento, config, 4294967294L);
        verifyNoMoreInteractions(store);
    }

    @Test
    void unGpsAsociadoCierraCirculacionInclusoSinInfracciones() throws Exception {
        asignador.accept(new Detection(GPS, null, List.of(), ResultadoAsociacionGpsGuia.asociado("G1")));
        verify(store).cerrarCirculacionPorGuia(GPS.licensePlate(), GPS.timestamp());
        verifyNoMoreInteractions(store);
    }

    @Test
    void unEventoSinInfraccionesNiGuiaNoGeneraOperaciones() {
        asignador.accept(sinGuia(GPS));
        asignador.accept(new Detection(new Weighing(3, 4, "SBC1234", GPS.timestamp(), 18390), null,
                List.of(), ResultadoAsociacionGpsGuia.asociado("G1")));
        verifyNoInteractions(store);
    }

    @Test
    void viajeSinGuiaContradiceUnaGuiaAsociada() {
        assertThrows(UncheckedIOException.class, () -> asignador.accept(new Detection(GPS, null,
                List.of(Situation.TRIP_WITHOUT_GUIDE), ResultadoAsociacionGpsGuia.asociado("G1"))));
    }

    @Test
    void unPesajeNoAbreCirculacion() {
        asignador.accept(sinGuia(new Weighing(3, 4, "SBC1234", GPS.timestamp(), 18390), Situation.TRIP_WITHOUT_GUIDE));
        verifyNoInteractions(store);
    }

    @Test
    void unDesvioNecesitaGpsYPruebaGeometrica() {
        for (VehicleEvent evento : List.of(GPS, new Weighing(3, 4, "SBC1234", GPS.timestamp(), 18390))) {
            assertThrows(UncheckedIOException.class, () -> asignador.accept(new Detection(evento, null,
                    List.of(Situation.ROUTE_DEVIATION), ResultadoAsociacionGpsGuia.asociado("G1"))));
        }
    }

    @Test
    void unaCirculacionPendienteNoSeNotifica() throws Exception {
        asignador.accept(sinGuia(GPS, Situation.TRIP_WITHOUT_GUIDE));
        verify(store).registrarCirculacionSinGuia(GPS);
        verifyNoMoreInteractions(store);
    }

    @Test
    void unRepositorioBasicoRechazaOperacionesNoImplementadas() {
        CaseStore basico = new CaseStore() {
            public DatosCaso registrar(TipoCasoAutomatico tipo, String guia, VehicleEvent evento) { return null; }
            public DatosCaso notify(long id, String correlacion) { return null; }
        };
        assertThrows(IOException.class, () -> basico.registrarDesvio("G1", GPS, null));
        assertThrows(IOException.class, () -> basico.registrarCirculacionSinGuia(GPS));
        assertThrows(IOException.class, () -> basico.registrarHabilitacion(null, GPS, null));
        assertDoesNotThrow(() -> basico.cerrarCirculacionPorGuia(GPS.licensePlate(), GPS.timestamp()));
    }
}

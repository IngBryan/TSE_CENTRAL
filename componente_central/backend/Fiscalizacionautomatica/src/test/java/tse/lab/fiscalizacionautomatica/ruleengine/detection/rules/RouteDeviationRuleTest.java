package tse.lab.fiscalizacionautomatica.ruleengine.detection.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.data.InMemoryTrackingEventDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.data.TrackingEventDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.VehicleFacts;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;
import tse.lab.fiscalizacionautomatica.ruleengine.routes.RutasAutorizadasSource;
import tse.lab.fiscalizacionautomatica.ruleengine.trips.IntervalosGuiasGpsSource;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervaloGuiaGps;
import tse.lab.frontofficeciudadanos.service.rutas.DatosRutaAutorizada;

class RouteDeviationRuleTest {
    private static final Instant T = Instant.parse("2026-10-31T12:00:00Z");
    private static final String MATRICULA = "SBC1234";
    private static final String RUTA_A = "{\"type\":\"LineString\",\"coordinates\":[[-56,-34.5],[-56,-34]]}";
    private static final String RUTA_B = "{\"type\":\"LineString\",\"coordinates\":[[-55.95,-34.5],[-55.95,-34]]}";
    private static final VehicleFacts HECHOS = new VehicleFacts(null, List.of());

    private final InMemoryTrackingEventDAO dao = new InMemoryTrackingEventDAO();
    private List<IntervaloGuiaGps> candidatos;
    private List<DatosRutaAutorizada> autorizadas;
    private RouteDeviationRule regla;

    @BeforeEach
    void setup() {
        candidatos = List.of(intervalo("G-101", "EN_CURSO", T.minusSeconds(3600), T.minusSeconds(600), null));
        autorizadas = List.of(ruta(1L, RUTA_A));
        regla = nuevaRegla();
    }

    private RouteDeviationRule nuevaRegla() {
        IntervalosGuiasGpsSource intervalos = (matricula, desde, hasta) -> candidatos;
        RutasAutorizadasSource rutas = idGuia -> autorizadas;
        return new RouteDeviationRule(intervalos, rutas, dao);
    }

    private static IntervaloGuiaGps intervalo(String id, String estado, Instant asignacion,
            Instant inicio, Instant fin) {
        return new IntervaloGuiaGps(id, MATRICULA, estado, asignacion, inicio, fin);
    }

    private static DatosRutaAutorizada ruta(long id, String geojson) {
        return new DatosRutaAutorizada(id, (int) id, "sha" + id, 1000, geojson);
    }

    private TrackingEvent gps(long segundos, double longitude) {
        TrackingEvent e = new TrackingEvent("msg-" + segundos, segundos, 5, MATRICULA, -34.25, longitude,
                T.plusSeconds(segundos));
        dao.save(e);
        return e;
    }

    @Test
    void dosFueraDeTodasLasRutasEn30SegundosConfirman() throws IOException {
        gps(0, -55.95);
        assertTrue(regla.appliesTo(gps(10, -55.951), HECHOS));
    }

    @Test
    void pruebaContieneAmbosPuntosDistanciasYTodasLasReferencias() throws IOException {
        TrackingEvent primero = gps(0, -55.95);
        TrackingEvent segundo = gps(10, -55.951);
        var prueba = regla.evaluar(segundo, HECHOS).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(segundo, prueba.confirmante());
        org.junit.jupiter.api.Assertions.assertEquals(primero, prueba.pareja());
        org.junit.jupiter.api.Assertions.assertEquals(List.of("sha1"), prueba.hashesRutas());
        assertTrue(prueba.distanciaConfirmanteMetros() > 2000);
        assertTrue(prueba.distanciaParejaMetros() > 2000);
    }

    @Test
    void unaSolaPosicionFueraNoConfirma() throws IOException {
        assertFalse(regla.appliesTo(gps(0, -55.95), HECHOS));
    }

    @Test
    void nuevosParametrosCambianLaDistanciaYLaVentanaSinRecrearElProceso() throws IOException {
        gps(0,-55.95);
        TrackingEvent segundo=gps(40,-55.95);
        assertFalse(regla.evaluar(segundo,HECHOS).isPresent());
        var amplia=new tse.lab.reglas.ConfiguracionReglas(2,java.math.BigDecimal.ZERO,0,2000,60,
                "2026-11-07T00:00:00Z","admin","x",true);
        assertTrue(regla.evaluar(segundo,HECHOS,amplia).isPresent());
        var corredor=new tse.lab.reglas.ConfiguracionReglas(3,java.math.BigDecimal.ZERO,0,10000,60,
                "2026-11-07T00:00:00Z","admin","x",true);
        assertFalse(regla.evaluar(segundo,HECHOS,corredor).isPresent());
    }

    @Test
    void posicionCercaDeLaRutaNoConfirma() throws IOException {
        gps(0, -56);
        assertFalse(regla.appliesTo(gps(10, -56.001), HECHOS));
    }

    @Test
    void unaAlternativaHistoricaYaNoEvitaElDesvio() throws IOException {
        autorizadas = List.of(ruta(1, RUTA_A), ruta(2, RUTA_B));
        regla = nuevaRegla();
        gps(0, -55.95);
        assertTrue(regla.appliesTo(gps(10, -55.95), HECHOS));
    }

    @Test
    void veintinueveSegundosConfirmaTreintaYUnoNo() throws IOException {
        gps(0, -55.95);
        assertTrue(regla.appliesTo(gps(29, -55.95), HECHOS));
        assertFalse(regla.appliesTo(gps(60, -55.95), HECHOS)); // 31 s desde el punto previo
    }

    @Test
    void puntoIntermedioDentroDelCorredorRompeLaConsecutividad() throws IOException {
        gps(0, -55.95);
        gps(10, -56);
        assertFalse(regla.appliesTo(gps(20, -55.95), HECHOS));
    }

    @Test
    void eventosFueraDeOrdenPuedenConfirmarse() throws IOException {
        gps(20, -55.95);
        assertTrue(regla.appliesTo(gps(10, -55.95), HECHOS));
    }

    @Test
    void sinGuiaNoEvaluaDesvio() throws IOException {
        candidatos = List.of();
        gps(0, -55.95);
        assertFalse(regla.appliesTo(gps(10, -55.95), HECHOS));
    }

    @Test
    void dosGuiasSuperpuestasSinInicioSonAmbiguas() throws IOException {
        candidatos = List.of(
                intervalo("G-101", "ASIGNADA", T.minusSeconds(3600), null, null),
                intervalo("G-102", "ASIGNADA", T.minusSeconds(3600), null, null));
        gps(0, -55.95);
        assertFalse(regla.appliesTo(gps(10, -55.95), HECHOS));
    }

    @Test
    void guiaFinalizadaSinFinFiableNoSeFiscaliza() throws IOException {
        candidatos = List.of(intervalo("G-101", "FINALIZADA", T.minusSeconds(3600), T.minusSeconds(600), null));
        gps(0, -55.95);
        assertFalse(regla.appliesTo(gps(10, -55.95), HECHOS));
    }

    @Test
    void posicionesAsignadasAOtraGuiaNoSonPareja() throws IOException {
        candidatos = List.of(
                intervalo("G-101", "FINALIZADA", T.minusSeconds(3600), T.minusSeconds(600), T.plusSeconds(5)),
                intervalo("G-102", "EN_CURSO", T.plusSeconds(5), T.plusSeconds(6), null));
        gps(0, -55.95);
        assertFalse(regla.appliesTo(gps(10, -55.95), HECHOS));
    }

    @Test
    void pesajeNoEsDatoEspacial() throws IOException {
        assertFalse(regla.appliesTo(new Weighing(1, 2, MATRICULA, T, 30_000), HECHOS));
    }

    @Test
    void noHayRutasAsociadasNoSeInventaUna() throws IOException {
        autorizadas = List.of();
        gps(0, -55.95);
        assertFalse(regla.appliesTo(gps(10, -55.95), HECHOS));
    }

    @Test
    void geometriaCorruptaFallaParaReintento() {
        autorizadas = List.of(ruta(1, "{mal-json}"));
        gps(0, -55.95);
        assertThrows(IOException.class, () -> regla.appliesTo(gps(10, -55.95), HECHOS));
    }

    @Test
    void falloDeConsultaDeGuiaPropagaIOException() {
        RouteDeviationRule caida = new RouteDeviationRule((m, d, h) -> { throw new IOException("EJB down"); },
                id -> autorizadas, dao);
        assertThrows(IOException.class, () -> caida.appliesTo(gps(10, -55.95), HECHOS));
    }

    @Test
    void evitaContarDuplicadosDeLaMismaMarcaDeTiempo() throws IOException {
        gps(0, -55.95);
        assertFalse(regla.appliesTo(gps(0, -55.95), HECHOS));
    }

    @Test
    void unFalsoCorredorRectoNoSustituyeLaPolilinea() throws IOException {
        autorizadas = List.of(ruta(1, "{\"type\":\"LineString\",\"coordinates\":[[-56.1,-34.5],[-56.1,-34.1],[-55.9,-34.1],[-55.9,-34.5]]}"));
        regla = nuevaRegla();
        gps(0, -56.0); // dentro del rectángulo, lejos de cualquier segmento de la ruta
        assertTrue(regla.appliesTo(gps(10, -56.0), HECHOS));
    }

    @Test
    void itIsARouteDeviation() {
        assertEquals(tse.lab.fiscalizacionautomatica.ruleengine.detection.Situation.ROUTE_DEVIATION, regla.situation());
    }

    private RouteDeviationRule conLimites(double umbral, Duration ventana) {
        return new RouteDeviationRule((m, d, h) -> candidatos, id -> autorizadas, dao, umbral, ventana);
    }

    @Test
    void aThresholdAndAWindowAreRequired() {
        Duration ventana = Duration.ofSeconds(30);

        assertThrows(IllegalArgumentException.class, () -> conLimites(Double.NaN, ventana));
        assertThrows(IllegalArgumentException.class, () -> conLimites(0, ventana));
        assertThrows(IllegalArgumentException.class, () -> conLimites(2000, null));
        assertThrows(IllegalArgumentException.class, () -> conLimites(2000, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> conLimites(2000, Duration.ofSeconds(-1)));
    }

    @Test
    void withoutAnswerOnTheRoutesThereIsNoDeviation() throws IOException {
        autorizadas = null;
        gps(0, -55.95);

        assertFalse(regla.appliesTo(gps(10, -55.95), HECHOS));
    }

    @Test
    void aTruncatedQueryDoesNotConfirm() throws IOException {
        TrackingEvent fuera = gps(0, -55.95);
        TrackingEventDAO lleno = mock(TrackingEventDAO.class);
        when(lleno.findByLicensePlateBetween(eq(MATRICULA), any(), any(), anyInt()))
                .thenReturn(Collections.nCopies(1_000, fuera));
        RouteDeviationRule truncada = new RouteDeviationRule((m, d, h) -> candidatos, id -> autorizadas, lleno);

        assertFalse(truncada.appliesTo(fuera, HECHOS));
    }

    @Test
    void aRouteWithoutHashCannotBeEvidence() {
        autorizadas = List.of(new DatosRutaAutorizada(1L, 1, " ", 1000, RUTA_A));
        gps(0, -55.95);
        TrackingEvent fuera = gps(10, -55.95);

        assertThrows(IOException.class, () -> regla.appliesTo(fuera, HECHOS));
    }

    @Test
    void theNextPositionCanConfirmToo() throws IOException {
        TrackingEvent primero = gps(0, -55.95);
        TrackingEvent siguiente = gps(10, -55.95);

        assertEquals(siguiente, regla.evaluar(primero, HECHOS).orElseThrow().pareja());
    }
}

package tse.lab.frontofficeciudadanos.service.rutas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.List;
import jakarta.json.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tse.lab.frontofficeciudadanos.data.LocalidadDAO;
import tse.lab.frontofficeciudadanos.data.RutaReferenciaDAO;
import tse.lab.model.Localidad;
import tse.lab.model.RutaReferencia;

class PrevisualizacionRutasServiceTest {
    private static final String GEO = "{\"type\":\"LineString\",\"coordinates\":[[0,0],[1,1]]}";
    private static final Localidad MERCEDES = new Localidad(1L, "Mercedes", "Soriano", -33.25, -58.03);
    private static final Localidad DOLORES = new Localidad(2L, "Dolores", "Soriano", -33.53, -58.21);

    private final LocalidadDAO localidades = mock(LocalidadDAO.class);
    private final OsrmRutasClient osrm = mock(OsrmRutasClient.class);
    private final PrevisualizacionRutasService servicio = new PrevisualizacionRutasService();

    private static void set(Object destino, String campo, Object valor) throws Exception {
        Field f = destino.getClass().getDeclaredField(campo);
        f.setAccessible(true);
        f.set(destino, valor);
    }

    @BeforeEach
    void setUp() throws Exception {
        set(servicio, "localidades", localidades);
        set(servicio, "osrm", osrm);
        when(localidades.buscarPorId(1L)).thenReturn(MERCEDES);
        when(localidades.buscarPorId(2L)).thenReturn(DOLORES);
    }

    private String codigoAlCalcular(Long origen, Long destino) {
        return assertThrows(RutasException.class, () -> servicio.calcular(origen, destino)).codigo();
    }

    @Test
    void asksTheRouterForBothLocalities() {
        when(osrm.obtener(MERCEDES, DOLORES)).thenReturn(List.of(new OsrmRutasClient.Candidata(GEO, 100, 60)));

        var previa = servicio.calcular(1L, 2L);

        assertEquals(1L, previa.origenId());
        assertEquals(2L, previa.destinoId());
        assertEquals(1, previa.rutas().getFirst().alternativa());
        assertEquals(100, previa.rutas().getFirst().distanciaMetros());
    }

    @Test
    void originAndDestinationMustBeDifferentAndExist() {
        assertEquals("localidades_invalidas", codigoAlCalcular(null, 2L));
        assertEquals("localidades_invalidas", codigoAlCalcular(1L, null));
        assertEquals("localidades_invalidas", codigoAlCalcular(1L, 1L));
        assertEquals("localidades_invalidas", codigoAlCalcular(9L, 2L));
        assertEquals("localidades_invalidas", codigoAlCalcular(1L, 9L));
        verify(osrm, never()).obtener(any(), any());
    }

    @Test
    void withoutCandidatesThereIsNoRoute() {
        for (List<OsrmRutasClient.Candidata> vacia : java.util.Arrays.asList(null, List.<OsrmRutasClient.Candidata>of())) {
            RutasException e = assertThrows(RutasException.class,
                    () -> PrevisualizacionRutasService.seleccionar(1L, 2L, vacia));
            assertEquals(422, e.status());
            assertEquals("sin_ruta_vial", e.codigo());
        }
    }

    @Test
    void anExistingGeometryIsReused() throws Exception {
        RutaReferenciaDAO dao = mock(RutaReferenciaDAO.class);
        RutaReferencia existente = mock(RutaReferencia.class);
        when(existente.getId()).thenReturn(3L);
        when(dao.buscarPorHuella(RutaReferencia.calcularHuella(GEO))).thenReturn(existente);
        GeometriasService geometrias = new GeometriasService();
        set(geometrias, "dao", dao);

        assertEquals(3L, geometrias.obtenerOCrear(GEO, 100));
        assertEquals(3L, geometrias.buscarId(RutaReferencia.calcularHuella(GEO)));
        verify(dao, never()).guardar(any());
    }

    @Test
    void aNewGeometryIsWrittenBeforeReturning() throws Exception {
        RutaReferenciaDAO dao = mock(RutaReferenciaDAO.class);
        RutaReferencia nueva = mock(RutaReferencia.class);
        when(nueva.getId()).thenReturn(4L);
        when(dao.guardar(any())).thenReturn(nueva);
        GeometriasService geometrias = new GeometriasService();
        set(geometrias, "dao", dao);

        assertEquals(4L, geometrias.obtenerOCrear(GEO, 100));
        assertNull(geometrias.buscarId("otra"));
        verify(dao).sincronizar();
    }

    private static String normalizar(String texto) {
        try (var lector = Json.createReader(new StringReader(texto))) {
            return GeoJsonRutas.normalizar(lector.readObject());
        }
    }

    private static void geometriaInvalida(String coordenadas) {
        assertThrows(RuntimeException.class,
                () -> normalizar("{\"type\":\"LineString\",\"coordinates\":" + coordenadas + "}"));
    }

    @Test
    void aGeometryIsALineStringOfPairsOfNumbers() {
        assertThrows(IllegalArgumentException.class, () -> GeoJsonRutas.normalizar(null));
        assertThrows(IllegalArgumentException.class, () -> normalizar("{\"type\":\"Point\"}"));
        assertThrows(IllegalArgumentException.class, () -> normalizar("{\"type\":\"LineString\"}"));
        geometriaInvalida("[[0,0]]");
        geometriaInvalida("[1,[0,0]]");
        geometriaInvalida("[[0],[1,1]]");
        geometriaInvalida("[[\"a\",0],[1,1]]");
        geometriaInvalida("[[0,\"a\"],[1,1]]");
        geometriaInvalida("[[0,91],[1,1]]");
        geometriaInvalida("[[0,-91],[1,1]]");
        geometriaInvalida("[[181,0],[1,1]]");
    }

    @Test
    void zeroIsWrittenWithoutDecimals() {
        assertEquals("{\"type\":\"LineString\",\"coordinates\":[[0,0],[1.5,1]]}",
                normalizar("{\"type\":\"LineString\",\"coordinates\":[[0.0000001,-0.0000001],[1.5,1.0]]}"));
    }
}

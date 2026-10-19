package tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.backofficeautoridadesadministradores.data.NodoPerifericoDAO;
import tse.lab.model.NodoBalanza;
import tse.lab.model.NodoPeriferico;
import tse.lab.model.NodoTracking;

class NodosActivosServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-19T13:00:00Z");

    record Comunicacion(String tipo, Long idExterno, Instant fecha) {
    }

    // DAO simulado: registra las actualizaciones pedidas
    static class NodoPerifericoDAOFalso extends NodoPerifericoDAO {

        final List<Comunicacion> registradas = new ArrayList<>();
        final List<NodoPeriferico> activos = new ArrayList<>();

        @Override
        public List<NodoPeriferico> listarActivos() {
            return activos;
        }

        @Override
        public int registrarComunicacion(String tipo, Long idExterno, Instant fecha) {
            registradas.add(new Comunicacion(tipo, idExterno, fecha));
            return 1;
        }
    }

    private final NodosActivosService servicio = new NodosActivosService();
    private final NodoPerifericoDAOFalso nodos = new NodoPerifericoDAOFalso();

    @BeforeEach
    void setUp() throws Exception {
        Field campo = NodosActivosService.class.getDeclaredField("nodos");
        campo.setAccessible(true);
        campo.set(servicio, nodos);
    }

    @Test
    void listaLosNodosActivosConSuTipoYUltimaComunicacion() {
        NodoBalanza balanza = new NodoBalanza("Ruta 8 - Km 150", "Ruta 8 km 150", 4L);
        balanza.setUltimaComunicacionExitosa(AHORA);
        nodos.activos.add(balanza);
        nodos.activos.add(new NodoTracking("GPS camion 1", "Montevideo", 1L));

        List<DatosNodo> datos = servicio.listarNodos();

        DatosNodo primero = datos.get(0);
        assertEquals(TipoNodo.BALANZA, primero.getTipo());
        assertNull(primero.getId());
        assertEquals(4L, primero.getIdExterno());
        assertEquals("Ruta 8 - Km 150", primero.getNombre());
        assertEquals("Ruta 8 km 150", primero.getUbicacion());
        assertTrue(primero.isActivo());
        assertEquals(AHORA, primero.getUltimaComunicacionExitosa());
        assertTrue(primero.toString().contains("idExterno=4"));
        assertEquals(TipoNodo.TRACKING, datos.get(1).getTipo());
        assertNull(datos.get(1).getUltimaComunicacionExitosa());
    }

    @Test
    void registraLaComunicacionDeCadaNodoConElDiscriminadorDelTipo() {
        servicio.registrarComunicaciones(TipoNodo.TRACKING, new TreeMap<>(Map.of(1L, AHORA, 2L,
                AHORA.minusSeconds(30))));

        assertEquals(List.of(new Comunicacion("TRACKING", 1L, AHORA),
                new Comunicacion("TRACKING", 2L, AHORA.minusSeconds(30))), nodos.registradas);
    }

    @Test
    void ignoraIdsYFechasNulos() {
        Map<Long, Instant> comunicaciones = new HashMap<>();
        comunicaciones.put(4L, AHORA);
        comunicaciones.put(5L, null);
        comunicaciones.put(null, AHORA);

        servicio.registrarComunicaciones(TipoNodo.BALANZA, comunicaciones);
        servicio.registrarComunicaciones(TipoNodo.BALANZA, null);

        assertEquals(List.of(new Comunicacion("BALANZA", 4L, AHORA)), nodos.registradas);
    }

    @Test
    void elTipoEsObligatorio() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrarComunicaciones(null, Map.of(4L, AHORA)));
    }
}

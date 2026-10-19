package tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

class NodosActivosServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-19T13:00:00Z");

    record Comunicacion(String tipo, Long idExterno, Instant fecha) {
    }

    // DAO simulado: registra las actualizaciones pedidas
    static class NodoPerifericoDAOFalso extends NodoPerifericoDAO {

        final List<Comunicacion> registradas = new ArrayList<>();

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

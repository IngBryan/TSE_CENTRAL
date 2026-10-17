
package tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.backofficeautoridadesadministradores.data.NodoPerifericoDAO;
import tse.lab.model.NodoBalanza;
import tse.lab.model.NodoPeriferico;
import tse.lab.model.NodoTracking;

class GestionNodosPerifericosServiceTest {

    private GestionNodosPerifericosService servicio;
    private NodoPerifericoDAOFalso nodos;

    // DAO simulado para probar el servicio sin PostgreSQL.
    static class NodoPerifericoDAOFalso extends NodoPerifericoDAO {

        private final List<NodoPeriferico> guardados = new ArrayList<>();

        @Override
        public void guardar(NodoPeriferico nodo) {
            guardados.add(nodo);
        }

        @Override
        public List<NodoPeriferico> listarTodos() {
            return new ArrayList<>(guardados);
        }

        @Override
        public boolean existePorTipoEIdExterno(
                Class<? extends NodoPeriferico> subtipo,
                Long idExterno) {

            return guardados.stream()
                    .anyMatch(nodo ->
                            subtipo.isInstance(nodo)
                            && Objects.equals(
                                    nodo.getIdExterno(), idExterno));
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        nodos = new NodoPerifericoDAOFalso();
        servicio = new GestionNodosPerifericosService();

        Field campo = GestionNodosPerifericosService.class
                .getDeclaredField("nodos");

        campo.setAccessible(true);
        campo.set(servicio, nodos);
    }

    @Test
    void registraBalanzaActiva() {
        DatosNodo resultado = servicio.registrar(
                TipoNodo.BALANZA,
                " Balanza Ruta 5 ",
                " Ruta 5 km 30 ",
                5L
        );

        assertEquals(TipoNodo.BALANZA, resultado.getTipo());
        assertEquals("Balanza Ruta 5", resultado.getNombre());
        assertEquals("Ruta 5 km 30", resultado.getUbicacion());
        assertEquals(5L, resultado.getIdExterno());
        assertTrue(resultado.isActivo());
        assertNull(resultado.getUltimaComunicacionExitosa());

        assertEquals(1, nodos.guardados.size());
        assertInstanceOf(NodoBalanza.class, nodos.guardados.get(0));
    }

    @Test
    void registraTrackingActivo() {
        DatosNodo resultado = servicio.registrar(
                TipoNodo.TRACKING,
                "Tracking Norte",
                "Artigas",
                10L
        );

        assertEquals(TipoNodo.TRACKING, resultado.getTipo());
        assertEquals(10L, resultado.getIdExterno());
        assertTrue(resultado.isActivo());

        assertEquals(1, nodos.guardados.size());
        assertInstanceOf(NodoTracking.class, nodos.guardados.get(0));
    }

    @Test
    void rechazaDuplicadosDelMismoTipo() {
        servicio.registrar(
                TipoNodo.BALANZA, "Balanza 1", "Ruta 5", 5L);

        assertThrows(
                NodoDuplicadoException.class,
                () -> servicio.registrar(
                        TipoNodo.BALANZA,
                        "Balanza 2",
                        "Ruta 8",
                        5L)
        );

        assertEquals(1, nodos.guardados.size());
    }

    @Test
    void permiteMismoIdExternoEnDistintosTipos() {
        servicio.registrar(
                TipoNodo.BALANZA, "Balanza", "Ruta 5", 5L);

        servicio.registrar(
                TipoNodo.TRACKING, "Tracking", "Artigas", 5L);

        assertEquals(2, nodos.guardados.size());
    }

    @Test
    void rechazaDatosObligatoriosInvalidos() {
        assertThrows(
                DatosNodoInvalidosException.class,
                () -> servicio.registrar(
                        null, "Nodo", "Montevideo", 1L));

        assertThrows(
                DatosNodoInvalidosException.class,
                () -> servicio.registrar(
                        TipoNodo.BALANZA, " ", "Ruta 5", 1L));

        assertThrows(
                DatosNodoInvalidosException.class,
                () -> servicio.registrar(
                        TipoNodo.BALANZA, "Nodo", null, 1L));

        assertThrows(
                DatosNodoInvalidosException.class,
                () -> servicio.registrar(
                        TipoNodo.TRACKING, "Nodo", "Artigas", null));

        assertTrue(nodos.guardados.isEmpty());
    }

    @Test
    void rechazaTextosDemasiadoLargos() {
        assertThrows(
                DatosNodoInvalidosException.class,
                () -> servicio.registrar(
                        TipoNodo.BALANZA,
                        "A".repeat(101),
                        "Ruta 5",
                        1L));

        assertThrows(
                DatosNodoInvalidosException.class,
                () -> servicio.registrar(
                        TipoNodo.BALANZA,
                        "Balanza",
                        "A".repeat(201),
                        1L));

        assertTrue(nodos.guardados.isEmpty());
    }

    @Test
    void listarTodosIncluyeActivosEInactivos() {
        servicio.registrar(
                TipoNodo.BALANZA, "Balanza", "Ruta 5", 5L);

        servicio.registrar(
                TipoNodo.TRACKING, "Tracking", "Artigas", 10L);

        Instant ultima = Instant.parse("2026-10-13T10:00:00Z");

        NodoPeriferico balanza = nodos.guardados.get(0);
        balanza.setActivo(false);
        balanza.setUltimaComunicacionExitosa(ultima);

        List<DatosNodo> resultado = servicio.listarTodos();

        assertEquals(2, resultado.size());

        assertFalse(resultado.get(0).isActivo());
        assertEquals(
                ultima,
                resultado.get(0).getUltimaComunicacionExitosa());

        assertTrue(resultado.get(1).isActivo());
    }
}

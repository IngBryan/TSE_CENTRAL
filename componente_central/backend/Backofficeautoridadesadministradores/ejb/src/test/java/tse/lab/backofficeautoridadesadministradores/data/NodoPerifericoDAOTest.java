package tse.lab.backofficeautoridadesadministradores.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import tse.lab.model.NodoBalanza;
import tse.lab.model.NodoPeriferico;
import tse.lab.model.NodoTracking;

// Consultas de nodos perifericos contra H2.
class NodoPerifericoDAOTest {

    private static final Instant T1 = Instant.parse("2026-10-19T13:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-19T13:30:00Z");

    private EntityManagerFactory emf;
    private EntityManager em;
    private final NodoPerifericoDAO nodos = new NodoPerifericoDAO();

    private NodoBalanza balanza;
    private NodoTracking gps;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("backofficeTestPersistenceUnit");
        em = emf.createEntityManager();
        Field campo = NodoPerifericoDAO.class.getDeclaredField("em");
        campo.setAccessible(true);
        campo.set(nodos, em);

        balanza = new NodoBalanza("Ruta 8 - Km 150", "Ruta 8 km 150", 4L);
        gps = new NodoTracking("GPS camion 1", "Montevideo", 4L);
        NodoBalanza inactiva = new NodoBalanza("Ruta 5 - Km 30", "Ruta 5 km 30", 1L);
        inactiva.setActivo(false);
        enTx(() -> {
            nodos.guardar(balanza);
            nodos.guardar(gps);
            nodos.guardar(inactiva);
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    private <T> T enTx(Supplier<T> accion) {
        em.getTransaction().begin();
        T resultado = accion.get();
        em.getTransaction().commit();
        return resultado;
    }

    private Instant ultimaComunicacion(NodoPeriferico nodo) {
        em.clear();
        return nodos.buscarPorId(nodo.getId()).getUltimaComunicacionExitosa();
    }

    @Test
    void listaTodosYSoloLosActivosOrdenadosPorId() {
        assertEquals(3, nodos.listarTodos().size());

        List<NodoPeriferico> activos = nodos.listarActivos();

        assertEquals(List.of(balanza.getId(), gps.getId()), activos.stream().map(NodoPeriferico::getId).toList());
        assertInstanceOf(NodoBalanza.class, nodos.buscarPorId(balanza.getId()));
    }

    @Test
    void elIdExternoSeRepiteSoloEntreTiposDistintos() {
        assertTrue(nodos.existePorTipoEIdExterno(NodoBalanza.class, 4L));
        assertTrue(nodos.existePorTipoEIdExterno(NodoTracking.class, 4L));
        assertFalse(nodos.existePorTipoEIdExterno(NodoTracking.class, 1L));
    }

    @Test
    void registraLaComunicacionSoloDelNodoDeEseTipo() {
        int actualizados = enTx(() -> nodos.registrarComunicacion("BALANZA", 4L, T1));

        assertEquals(1, actualizados);
        assertEquals(T1, ultimaComunicacion(balanza));
        assertNull(ultimaComunicacion(gps));
    }

    @Test
    void laUltimaComunicacionNuncaRetrocede() {
        enTx(() -> nodos.registrarComunicacion("TRACKING", 4L, T2));

        int actualizados = enTx(() -> nodos.registrarComunicacion("TRACKING", 4L, T1));

        assertEquals(0, actualizados);
        assertEquals(T2, ultimaComunicacion(gps));
    }

    @Test
    void unIdExternoInexistenteNoActualizaNada() {
        assertEquals(0, (int) enTx(() -> nodos.registrarComunicacion("BALANZA", 99L, T1)));
    }
}

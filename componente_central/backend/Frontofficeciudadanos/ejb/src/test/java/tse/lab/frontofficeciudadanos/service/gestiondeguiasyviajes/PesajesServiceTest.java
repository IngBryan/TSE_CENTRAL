package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficeciudadanos.data.EventoPesajesDAO;
import tse.lab.frontofficeciudadanos.data.NodoBalanzaDAO;
import tse.lab.model.NodoBalanza;

// Pesajes del poller de la fiscalizacion automatica (EJB remoto) contra H2 con los DAOs reales.
class PesajesServiceTest {

    private static final Instant T1 = Instant.parse("2026-10-13T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-13T11:00:00Z");

    private EntityManagerFactory emf;
    private EntityManager em;
    private PesajesService servicio;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficeciudadanosTestPersistenceUnit");
        em = emf.createEntityManager();
        EventoPesajesDAO pesajes = new EventoPesajesDAO();
        set(pesajes, "em", em);
        NodoBalanzaDAO balanzas = new NodoBalanzaDAO();
        set(balanzas, "em", em);
        servicio = new PesajesService();
        set(servicio, "pesajes", pesajes);
        set(servicio, "balanzas", balanzas);

        em.getTransaction().begin();
        em.persist(new NodoBalanza(
            "Balanza Ruta 8",
            "Ruta 8 - Km 150",
            4L
        ));
        em.getTransaction().commit();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    private static void set(Object destino, String campo, Object valor) throws ReflectiveOperationException {
        Field field = destino.getClass().getDeclaredField(campo);
        field.setAccessible(true);
        field.set(destino, valor);
    }

    // Cada llamada al EJB es una transaccion
    private <T> T enTransaccion(Supplier<T> accion) {
        em.getTransaction().begin();
        T resultado = accion.get();
        em.getTransaction().commit();
        return resultado;
    }

    private static DatosPesaje pesaje(long id, String matricula, Instant fechaHora) {
        return new DatosPesaje(id, 4, matricula, fechaHora, 18_450);
    }

    @Test
    void guardaLosPesajesPendientesDeDeteccion() {
        int guardados = enTransaccion(() -> servicio.guardarPesajes(
                List.of(pesaje(3, "SBC1234", T1), pesaje(5, "STU5678", T2))));

        assertEquals(2, guardados);
        assertEquals(List.of(pesaje(3, "SBC1234", T1), pesaje(5, "STU5678", T2)),
                enTransaccion(() -> servicio.listarPendientes(100)));
    }

    @Test
    void ignoraLosPesajesQueYaExisten() {
        enTransaccion(() -> servicio.guardarPesajes(List.of(pesaje(3, "SBC1234", T1))));

        // Mismo pesaje traido otra vez (el poller repite un rango) y repetido dentro de la misma lista
        int guardados = enTransaccion(() -> servicio.guardarPesajes(
                List.of(pesaje(3, "SBC1234", T1), pesaje(5, "STU5678", T2), pesaje(5, "STU5678", T2))));

        assertEquals(1, guardados);
        assertEquals(2, enTransaccion(() -> servicio.listarPendientes(100)).size());
    }

    @Test
    void ignoraLosPesajesDeBalanzasNoDadasDeAlta() {
        int guardados = enTransaccion(() -> servicio.guardarPesajes(
                List.of(new DatosPesaje(9, 99, "SBC1234", T1, 18_450))));

        assertEquals(0, guardados);
        assertEquals(List.of(), enTransaccion(() -> servicio.listarPendientes(100)));
    }

    @Test
    void unPesajeMarcadoComoDetectadoDejaDeEstarPendiente() {
        enTransaccion(() -> servicio.guardarPesajes(List.of(pesaje(3, "SBC1234", T1), pesaje(5, "STU5678", T2))));

        enTransaccion(() -> {
            servicio.marcarDetectado("SBC1234", T1);
            return null;
        });

        assertEquals(List.of(pesaje(5, "STU5678", T2)), enTransaccion(() -> servicio.listarPendientes(100)));
    }
}

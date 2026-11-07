package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tse.lab.frontofficeciudadanos.data.EventoViajeDAO;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.model.Chofer;
import tse.lab.model.Ciudadano;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoChofer;
import tse.lab.model.EstadoGuia;
import tse.lab.model.EventoViaje;
import tse.lab.model.Guia;
import tse.lab.model.ResultadoEventoViaje;
import tse.lab.model.TipoEventoViaje;
import tse.lab.model.Vehiculo;

/** Verifica las consultas JPQL contra la unidad de persistencia H2 usada por el proyecto. */
class IntervalosGuiasGpsJpaTest {
    private EntityManagerFactory emf;
    private EntityManager em;

    private static void inject(Object owner, String field, Object value) throws Exception {
        Field f = owner.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(owner, value);
    }

    @AfterEach
    void tearDown() {
        if (em != null) em.close();
        if (emf != null) emf.close();
    }

    @Test
    void reconstruyeInicioYFinAplicadosDeUnaGuiaFinalizada() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficeciudadanosTestPersistenceUnit");
        em = emf.createEntityManager();
        Empresa empresa = new Empresa(99L, "Empresa", "Razon", "Direccion", null);
        Vehiculo v = new Vehiculo("SBC1234", "Scania", "R", 9000, 18000, empresa);
        Ciudadano persona = new Ciudadano("12345678", null, null);
        Chofer chofer = new Chofer(persona, empresa, EstadoChofer.HABILITADO);
        Guia g = new Guia("G-RECONSTRUIDA", "Forestal", 1000,
                LocalDate.of(2026, 10, 10), "A", "B", empresa);
        g.asignar(v, chofer);
        Instant asignada = g.getFechaAsignacion();
        g.cambiarEstado(EstadoGuia.FINALIZADA);
        em.getTransaction().begin();
        for (Object entity : List.of(empresa, v, persona, chofer, g)) em.persist(entity);
        EventoViaje inicio = new EventoViaje("inicio-gps", g, persona.getCi(), TipoEventoViaje.INICIO,
                asignada.plusSeconds(10), null, null, null, null);
        inicio.registrarResultado(ResultadoEventoViaje.APLICADO, null, null, EstadoGuia.EN_CURSO, null);
        EventoViaje finConflictivo = new EventoViaje("fin-rechazado", g, persona.getCi(), TipoEventoViaje.FIN,
                asignada.plusSeconds(15), null, null, null, null);
        finConflictivo.registrarResultado(ResultadoEventoViaje.CONFLICTO, "ERROR", null, EstadoGuia.EN_CURSO, null);
        EventoViaje fin = new EventoViaje("fin-gps", g, persona.getCi(), TipoEventoViaje.FIN,
                asignada.plusSeconds(60), null, null, null, null);
        fin.registrarResultado(ResultadoEventoViaje.APLICADO, null, null, EstadoGuia.FINALIZADA, null);
        for (Object entity : List.of(inicio, finConflictivo, fin)) em.persist(entity);
        em.getTransaction().commit();
        em.clear();
        // Recuperamos la precision temporal realmente persistida por H2.
        Instant asignacionPersistida =
                em.find(Guia.class, g.getId()).getFechaAsignacion();

        GuiaDAO guiaDAO = new GuiaDAO();
        EventoViajeDAO eventoDAO = new EventoViajeDAO();
        inject(guiaDAO, "em", em);
        inject(eventoDAO, "em", em);
        IntervalosGuiasGpsService service = new IntervalosGuiasGpsService();
        inject(service, "guias", guiaDAO);
        inject(service, "eventos", eventoDAO);

        List<IntervaloGuiaGps> result = service.buscar(
        "SBC1234",
                asignacionPersistida.plusSeconds(20),
                asignacionPersistida.plusSeconds(90)
        );
        assertEquals(1, result.size());
        assertEquals("G-RECONSTRUIDA", result.getFirst().getIdGuia());

        assertEquals(
                asignacionPersistida.plusSeconds(10),
                result.getFirst().getInicioAplicado()
        );

        assertEquals(
                asignacionPersistida.plusSeconds(60),
                result.getFirst().getFinAplicado()
        );
    }
}

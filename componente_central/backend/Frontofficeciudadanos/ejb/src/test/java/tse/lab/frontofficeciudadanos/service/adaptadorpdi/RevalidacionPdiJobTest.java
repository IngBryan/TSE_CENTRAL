package tse.lab.frontofficeciudadanos.service.adaptadorpdi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficeciudadanos.data.CiudadanoDAO;
import tse.lab.model.Ciudadano;
import tse.lab.model.EstadoValidacion;

// CU-04 contra H2: los pendientes se reenvian a la PDI y, mientras no responde, la revision se aleja.
class RevalidacionPdiJobTest {

    private EntityManagerFactory emf;
    private EntityManager em;
    private PdiFalsa pdi;
    private RevalidacionPdiJob job;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficeciudadanosTestPersistenceUnit");
        em = emf.createEntityManager();
        CiudadanoDAO perfiles = new CiudadanoDAO();
        set(perfiles, "em", em);
        pdi = new PdiFalsa();
        job = new RevalidacionPdiJob();
        set(job, "perfiles", perfiles);
        set(job, "validacion", pdi);

        em.getTransaction().begin();
        for (String ci : List.of("11111111", "22222222")) {
            em.persist(new Ciudadano(ci, ci + "@prueba.uy", "sub-" + ci));
        }
        Ciudadano validado = new Ciudadano("44444444", "v@prueba.uy", "sub-v");
        validado.setEstado(EstadoValidacion.VALIDADO);
        em.persist(validado);
        em.getTransaction().commit();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    void conLaPdiCaidaLaProximaRevisionSeAlejaDeFormaExponencial() {
        pdi.disponible = false;

        assertEquals(Duration.ofSeconds(60), job.revalidarPendientes());
        assertEquals(Duration.ofSeconds(120), job.revalidarPendientes());
        assertEquals(Duration.ofSeconds(240), job.revalidarPendientes());
        // Corta en el primer NO_DISPONIBLE: no le sigue pegando a la PDI con el resto
        assertEquals(3, pdi.consultadas.size());
    }

    @Test
    void cuandoLaPdiVuelveSeValidanLosPendientesYSeVuelveAlIntervaloBase() {
        pdi.disponible = false;
        job.revalidarPendientes();
        job.revalidarPendientes();

        pdi.disponible = true;
        pdi.consultadas.clear();
        assertEquals(Duration.ofSeconds(30), job.revalidarPendientes());

        // Solo los pendientes; el que ya estaba validado no se consulta
        assertEquals(List.of("11111111", "22222222"), pdi.consultadas.stream().sorted().toList());
        em.clear();
        assertEquals(0, new ArrayList<>(em.createQuery(
                "SELECT p FROM Ciudadano p WHERE p.estado = :e", Ciudadano.class)
                .setParameter("e", EstadoValidacion.PENDIENTE_VALIDACION).getResultList()).size());

        // Sin pendientes no se consulta
        pdi.consultadas.clear();
        assertEquals(Duration.ofSeconds(30), job.revalidarPendientes());
        assertEquals(0, pdi.consultadas.size());
    }

    private class PdiFalsa extends ValidacionPdiService {
        boolean disponible = true;
        final List<String> consultadas = new ArrayList<>();

        @Override
        public ResultadoValidacion validar(Ciudadano perfil, String correlationId) {
            consultadas.add(perfil.getCi());
            if (!disponible) {
                return ResultadoValidacion.NO_DISPONIBLE;
            }
            em.getTransaction().begin();
            perfil.setEstado(EstadoValidacion.VALIDADO);
            em.merge(perfil);
            em.getTransaction().commit();
            return ResultadoValidacion.VALIDADO;
        }
    }

    private static void set(Object destino, String campo, Object valor) throws Exception {
        Field f = destino.getClass().getDeclaredField(campo);
        f.setAccessible(true);
        f.set(destino, valor);
    }
}

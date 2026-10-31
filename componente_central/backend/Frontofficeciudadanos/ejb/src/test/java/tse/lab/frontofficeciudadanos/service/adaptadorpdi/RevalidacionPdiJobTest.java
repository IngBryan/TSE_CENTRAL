package tse.lab.frontofficeciudadanos.service.adaptadorpdi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;
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
    private final TimerService timers = mock(TimerService.class);
    private final DataSource database = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);

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
        set(job, "timers", timers);
        set(job, "database", database);
        when(database.getConnection()).thenReturn(connection);

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

    @Test
    void theReplicaThatTakesTheLockRevisesAndThenReleasesIt() throws Exception {
        lockTaken(true);

        job.revisar();

        assertEquals(List.of("11111111", "22222222"), pdi.consultadas.stream().sorted().toList());
        verify(connection).prepareStatement("SELECT pg_advisory_unlock(?)");
        verify(connection).close();
        verify(timers).createSingleActionTimer(eq(30_000L), any(TimerConfig.class));
    }

    @Test
    void whileAnotherReplicaHoldsTheLockTheRoundIsSkipped() throws Exception {
        lockTaken(false);

        job.revisar();

        assertTrue(pdi.consultadas.isEmpty());
        verify(connection, never()).prepareStatement("SELECT pg_advisory_unlock(?)");
        verify(timers).createSingleActionTimer(eq(30_000L), any(TimerConfig.class));
    }

    @Test
    void withoutTheDatabaseTheRoundIsRetriedLater() throws Exception {
        when(database.getConnection()).thenThrow(new SQLException("PostgreSQL down"));

        job.revisar();

        assertTrue(pdi.consultadas.isEmpty());
        verify(timers).createSingleActionTimer(eq(30_000L), any(TimerConfig.class));
    }

    /** pg_try_advisory_lock answers {@code taken}; pg_advisory_unlock always works. */
    private void lockTaken(boolean taken) throws SQLException {
        PreparedStatement tryLock = query(taken);
        PreparedStatement unlock = query(true);
        when(connection.prepareStatement("SELECT pg_try_advisory_lock(?)")).thenReturn(tryLock);
        when(connection.prepareStatement("SELECT pg_advisory_unlock(?)")).thenReturn(unlock);
    }

    private static PreparedStatement query(boolean result) throws SQLException {
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getBoolean(1)).thenReturn(result);
        return statement;
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

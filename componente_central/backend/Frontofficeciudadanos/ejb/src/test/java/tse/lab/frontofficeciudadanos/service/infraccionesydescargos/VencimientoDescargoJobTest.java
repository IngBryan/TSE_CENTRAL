package tse.lab.frontofficeciudadanos.service.infraccionesydescargos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.sql.DataSource;

import jakarta.ejb.ScheduleExpression;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// El job solo recorre: la regla del plazo y la transicion son de InfraccionesService (InfraccionesServiceTest).
class VencimientoDescargoJobTest {

    private InfraccionesFalso infracciones;
    private VencimientoDescargoJob job;
    private final TimerService timers = mock(TimerService.class);
    private final DataSource database = mock(DataSource.class);
    private final Connection connection = mock(Connection.class);

    @BeforeEach
    void setUp() throws Exception {
        infracciones = new InfraccionesFalso();
        job = new VencimientoDescargoJob(true);
        set(job, "infracciones", infracciones);
        set(job, "timers", timers);
        set(job, "database", database);
        when(database.getConnection()).thenReturn(connection);
    }

    @Test
    void correTodosLosDiasALas0005DeUruguayYUnaVezAlArrancar() {
        job.iniciar();

        ArgumentCaptor<ScheduleExpression> cuando = ArgumentCaptor.forClass(ScheduleExpression.class);
        verify(timers).createCalendarTimer(cuando.capture(), any(TimerConfig.class));
        assertEquals("0", cuando.getValue().getHour());
        assertEquals("5", cuando.getValue().getMinute());
        assertEquals("0", cuando.getValue().getSecond());
        assertEquals("America/Montevideo", cuando.getValue().getTimezone());
        verify(timers).createSingleActionTimer(eq(VencimientoDescargoJob.ESPERA_INICIAL_MS), any(TimerConfig.class));
    }

    @Test
    void theLogConfirmsThatTheTimersAreProgrammed() {
        List<String> lineas = capturar(job::iniciar);

        assertEquals(1, lineas.size());
        assertTrue(lineas.get(0).contains("\"evento\":\"descargo.vencimiento\""), lineas.get(0));
        assertTrue(lineas.get(0).contains("\"resultado\":\"programado\""), lineas.get(0));
    }

    @Test
    void turnedOffItProgramsNothing() throws Exception {
        VencimientoDescargoJob apagado = new VencimientoDescargoJob(false);
        set(apagado, "timers", timers);

        apagado.iniciar();

        verifyNoInteractions(timers);
    }

    @Test
    void aRoundWithoutExpiredCasesStillLeavesItsLine() {
        List<String> lineas = capturar(job::vencerPlazos);

        assertEquals(1, lineas.size());
        assertTrue(lineas.get(0).contains("\"vencidos\":0"), lineas.get(0));
    }

    @Test
    void siUnCasoFallaSigueConLosDemas() {
        infracciones.vencidos = List.of(1L, 2L, 3L);
        infracciones.fallan = Set.of(2L);

        assertEquals(2, job.vencerPlazos());

        assertEquals(List.of(1L, 2L, 3L), infracciones.intentados);
        // Todos con el mismo correlationId de la corrida
        assertEquals(1, infracciones.correlationIds.stream().distinct().count());
        assertTrue(infracciones.correlationIds.get(0).startsWith("vencimiento-"));
    }

    @Test
    void losQueYaNoCorrespondenNoSeCuentan() {
        // vencerPlazo devuelve false si mientras tanto se presento un descargo
        infracciones.vencidos = List.of(1L, 2L);
        infracciones.yaNoCorresponden = Set.of(1L);

        assertEquals(1, job.vencerPlazos());
    }

    @Test
    void theReplicaThatTakesTheLockRunsTheRoundAndThenReleasesIt() throws Exception {
        lockTaken(true);
        infracciones.vencidos = List.of(7L);

        job.revisar();

        assertEquals(List.of(7L), infracciones.intentados);
        verify(connection).prepareStatement("SELECT pg_advisory_unlock(?)");
        verify(connection).close();
    }

    @Test
    void whileAnotherReplicaHoldsTheLockTheRoundIsSkipped() throws Exception {
        lockTaken(false);
        infracciones.vencidos = List.of(7L);

        job.revisar();

        assertTrue(infracciones.intentados.isEmpty());
        verify(connection, never()).prepareStatement("SELECT pg_advisory_unlock(?)");
    }

    @Test
    void withoutTheDatabaseTheRoundIsSkippedUntilTheNextOne() throws Exception {
        when(database.getConnection()).thenThrow(new SQLException("PostgreSQL down"));
        infracciones.vencidos = List.of(7L);

        job.revisar();

        assertTrue(infracciones.intentados.isEmpty());
    }

    @Test
    void aRoundThatFailsReleasesTheLock() throws Exception {
        lockTaken(true);
        infracciones.consultaFalla = true;

        job.revisar();

        verify(connection).prepareStatement("SELECT pg_advisory_unlock(?)");
    }

    private static List<String> capturar(Runnable accion) {
        List<String> lineas = new ArrayList<>();
        Handler captura = new Handler() {
            @Override
            public void publish(LogRecord r) {
                lineas.add(r.getMessage());
            }

            @Override
            public void flush() {
                // Nothing buffered
            }

            @Override
            public void close() {
                // Nothing to release
            }
        };
        Logger logger = Logger.getLogger(VencimientoDescargoJob.class.getName());
        logger.addHandler(captura);
        try {
            accion.run();
        } finally {
            logger.removeHandler(captura);
        }
        return lineas;
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

    private static class InfraccionesFalso extends InfraccionesService {
        List<Long> vencidos = List.of();
        boolean consultaFalla;
        Set<Long> fallan = Set.of();
        Set<Long> yaNoCorresponden = Set.of();
        final List<Long> intentados = new ArrayList<>();
        final List<String> correlationIds = new ArrayList<>();

        InfraccionesFalso() {
            super(10, Clock.systemUTC());
        }

        @Override
        public List<Long> casosConPlazoVencido() {
            if (consultaFalla) {
                throw new IllegalStateException("falla de prueba");
            }
            return vencidos;
        }

        @Override
        public boolean vencerPlazo(Long idCaso, String correlationId) {
            intentados.add(idCaso);
            correlationIds.add(correlationId);
            if (fallan.contains(idCaso)) {
                throw new IllegalStateException("falla de prueba");
            }
            return !yaNoCorresponden.contains(idCaso);
        }
    }

    private static void set(Object destino, String campo, Object valor) throws Exception {
        Field f = destino.getClass().getDeclaredField(campo);
        f.setAccessible(true);
        f.set(destino, valor);
    }
}

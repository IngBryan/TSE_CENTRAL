package tse.lab.frontofficeciudadanos.service.adaptadorpdi;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.EJB;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.Timeout;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import javax.sql.DataSource;
import tse.lab.adaptadorpdi.Backoff;
import tse.lab.frontofficeciudadanos.data.CiudadanoDAO;
import tse.lab.model.Ciudadano;
import tse.lab.model.EstadoValidacion;

// CU-04 Reintentar validaciones pendientes. Los perfiles que quedaron en PENDIENTE_VALIDACION porque la
// PDI no respondia (CU-03 FA3) se vuelven a mandar a la PDI cada PDI_REVALIDACION_S segundos (30). Si la
// PDI sigue sin responder, la proxima revision se aleja de forma exponencial (30 s, 60 s, 120 s...) hasta
// PDI_REVALIDACION_MAX_S (600); en cuanto responde se validan todos los pendientes (edad incluida, y los
// choferes que esperaban quedan habilitados) y se vuelve al intervalo base. PDI_REVALIDACION_S=0 lo apaga.
@Singleton
@Startup
public class RevalidacionPdiJob {

    private static final Logger LOG = Logger.getLogger(RevalidacionPdiJob.class.getName());
    /**
     * With several WildFly replicas each one has this timer: only the one holding this PostgreSQL advisory lock
     * revises, so the same profiles are never sent to the PDI twice at the same time.
     */
    private static final long LOCK = "RevalidacionPdiJob".hashCode();

    @Resource
    private TimerService timers;

    @Resource(lookup = "java:/MainDS")
    private DataSource database;

    @EJB
    private CiudadanoDAO perfiles;

    @EJB
    private ValidacionPdiService validacion;

    private final Duration intervalo = Duration.ofSeconds(Long.parseLong(env("PDI_REVALIDACION_S", "30")));
    private final Duration intervaloMax = Duration.ofSeconds(Long.parseLong(env("PDI_REVALIDACION_MAX_S", "600")));

    // Revisiones seguidas en las que la PDI no respondio
    private int fallosSeguidos;

    @PostConstruct
    void iniciar() {
        if (intervalo.isZero()) {
            LOG.info("evento=pdi.revalidacion resultado=apagada");
            return;
        }
        programar(intervalo);
    }

    @Timeout
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    void revisar() {
        Duration proxima = intervalo;
        try (Connection connection = database.getConnection()) {
            if (advisoryLock(connection, "pg_try_advisory_lock")) {
                try {
                    proxima = revalidarPendientes();
                } finally {
                    advisoryLock(connection, "pg_advisory_unlock");
                }
            } else {
                LOG.fine("evento=pdi.revalidacion resultado=otra_replica");
            }
        } catch (SQLException | RuntimeException e) {
            LOG.log(Level.WARNING, "evento=pdi.revalidacion resultado=error", e);
        } finally {
            programar(proxima);
        }
    }

    /**
     * Takes or releases the lock in the session of the connection (it isn't tied to a transaction: each profile
     * is validated in its own one). If the connection dies before releasing it, PostgreSQL releases it.
     */
    private static boolean advisoryLock(Connection connection, String function) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT " + function + "(?)")) {
            query.setLong(1, LOCK);
            try (ResultSet result = query.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    // Una revision: devuelve cuanto esperar hasta la siguiente. Cada perfil se valida en su propia
    // transaccion (ValidacionPdiService), asi un fallo no deshace lo que ya se valido.
    Duration revalidarPendientes() {
        List<Ciudadano> pendientes = perfiles.buscarPorEstado(EstadoValidacion.PENDIENTE_VALIDACION);
        if (pendientes.isEmpty()) {
            fallosSeguidos = 0;
            return intervalo;
        }

        String correlationId = "revalidacion-" + UUID.randomUUID();
        Map<ResultadoValidacion, Integer> resultados = new EnumMap<>(ResultadoValidacion.class);
        for (Ciudadano perfil : pendientes) {
            ResultadoValidacion r = validacion.validar(perfil, correlationId);
            if (r == ResultadoValidacion.NO_DISPONIBLE) {
                // La PDI sigue caida: no tiene sentido seguir con el resto, se espera mas para la proxima
                fallosSeguidos++;
                Duration proxima = Backoff.exponencial(fallosSeguidos + 1, intervalo, intervaloMax);
                LOG.warning(() -> "evento=pdi.revalidacion correlationId=" + correlationId
                        + " resultado=pdi_no_disponible pendientes=" + pendientes.size() + " validados=" + resultados
                        + " proximoIntentoS=" + proxima.toSeconds());
                return proxima;
            }
            resultados.merge(r, 1, Integer::sum);
        }
        fallosSeguidos = 0;
        LOG.info(() -> "evento=pdi.revalidacion correlationId=" + correlationId + " resultado=ok pendientes="
                + pendientes.size() + " validados=" + resultados);
        return intervalo;
    }

    private void programar(Duration espera) {
        timers.createSingleActionTimer(espera.toMillis(), new TimerConfig(null, false));
    }

    private static String env(String nombre, String porDefecto) {
        String valor = System.getenv(nombre);
        return valor == null || valor.isBlank() ? porDefecto : valor;
    }
}

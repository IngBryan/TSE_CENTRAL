package tse.lab.frontofficeciudadanos.service.infraccionesydescargos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.EJB;
import jakarta.ejb.ScheduleExpression;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.Timeout;
import jakarta.ejb.TimerConfig;
import jakarta.ejb.TimerService;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import javax.sql.DataSource;

// Vencimiento del plazo de descargo: los casos NOTIFICADA a los que se les vencio el plazo sin descargo pasan
// solos a EN_DESCARGO, asi el funcionario los puede resolver. Corre todos los dias a las 00:05 de Uruguay (el
// plazo vence a las 00:00) y una vez al arrancar, por si WildFly estaba caido a esa hora. La regla del plazo
// no esta aca: es la de InfraccionesService, la misma que ve la empresa y que controla presentarDescargo.
// VENCIMIENTO_DESCARGO_ACTIVO=false lo apaga.
@Singleton
@Startup
public class VencimientoDescargoJob {

    private static final Logger LOG = Logger.getLogger(VencimientoDescargoJob.class.getName());
    // Con varias replicas de WildFly cada una tiene este timer: corre la revision solo la que tiene este advisory
    // lock de PostgreSQL. No protege los datos (vencerPlazo ya es idempotente), solo evita hacer el trabajo dos veces.
    private static final long LOCK = "VencimientoDescargoJob".hashCode();
    static final long ESPERA_INICIAL_MS = 60_000;

    @Resource
    private TimerService timers;

    @Resource(lookup = "java:/MainDS")
    private DataSource database;

    @EJB
    private InfraccionesService infracciones;

    private final boolean activo = !"false".equalsIgnoreCase(env("VENCIMIENTO_DESCARGO_ACTIVO", "true").trim());

    @PostConstruct
    void iniciar() {
        if (!activo) {
            LOG.info("evento=descargo.vencimiento resultado=apagado");
            return;
        }
        timers.createCalendarTimer(new ScheduleExpression().hour(0).minute(5).second(0)
                .timezone(InfraccionesService.URUGUAY.getId()), new TimerConfig(null, false));
        timers.createSingleActionTimer(ESPERA_INICIAL_MS, new TimerConfig(null, false));
    }

    @Timeout
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    void revisar() {
        try (Connection connection = database.getConnection()) {
            if (advisoryLock(connection, "pg_try_advisory_lock")) {
                try {
                    vencerPlazos();
                } finally {
                    advisoryLock(connection, "pg_advisory_unlock");
                }
            } else {
                LOG.fine("evento=descargo.vencimiento resultado=otra_replica");
            }
        } catch (SQLException | RuntimeException e) {
            LOG.log(Level.WARNING, "evento=descargo.vencimiento resultado=error", e);
        }
    }

    private static boolean advisoryLock(Connection connection, String function) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT " + function + "(?)")) {
            query.setLong(1, LOCK);
            try (ResultSet result = query.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    // Una revision: devuelve cuantos casos paso a EN_DESCARGO. Cada caso va en su propia transaccion
    // (InfraccionesService.vencerPlazo): si uno falla se sigue con el resto y se reintenta en la proxima.
    int vencerPlazos() {
        List<Long> vencidos = infracciones.casosConPlazoVencido();
        if (vencidos.isEmpty()) {
            LOG.fine("evento=descargo.vencimiento resultado=ok vencidos=0");
            return 0;
        }
        String correlationId = "vencimiento-" + UUID.randomUUID();
        int pasados = 0;
        int fallidos = 0;
        for (Long idCaso : vencidos) {
            try {
                if (infracciones.vencerPlazo(idCaso, correlationId)) {
                    pasados++;
                }
            } catch (RuntimeException e) {
                fallidos++;
                LOG.log(Level.WARNING, "evento=descargo.vencimiento correlationId=" + correlationId + " caso="
                        + idCaso + " resultado=error", e);
            }
        }
        int ok = pasados;
        int error = fallidos;
        LOG.info(() -> "evento=descargo.vencimiento correlationId=" + correlationId + " resultado=ok vencidos="
                + vencidos.size() + " enDescargo=" + ok + " fallidos=" + error);
        return pasados;
    }

    private static String env(String nombre, String porDefecto) {
        String valor = System.getenv(nombre);
        return valor == null || valor.isBlank() ? porDefecto : valor;
    }
}

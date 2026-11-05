package tse.lab.frontofficeciudadanos.service.infraccionesydescargos;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
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
import tse.lab.frontofficeciudadanos.service.AdvisoryLock;
import tse.lab.frontofficeciudadanos.service.LogCiudadanos;

// Vencimiento del plazo de descargo: los casos NOTIFICADA a los que se les vencio el plazo sin descargo pasan
// solos a EN_DESCARGO, asi el funcionario los puede resolver. Corre todos los dias a las 00:05 de Uruguay (el
// plazo vence a las 00:00) y una vez al arrancar, por si WildFly estaba caido a esa hora. La regla del plazo
// no esta aca: es la de InfraccionesService, la misma que ve la empresa y que controla presentarDescargo.
// VENCIMIENTO_DESCARGO_ACTIVO=false lo apaga.
@Singleton
@Startup
public class VencimientoDescargoJob {

    private static final Logger LOG = Logger.getLogger(VencimientoDescargoJob.class.getName());
    private static final String EVENTO = "descargo.vencimiento";
    private static final String RESULTADO = "resultado";
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

    private final boolean activo;

    public VencimientoDescargoJob() {
        this(activoConfigurado());
    }

    // For the tests: without reading the environment
    VencimientoDescargoJob(boolean activo) {
        this.activo = activo;
    }

    @PostConstruct
    void iniciar() {
        if (!activo) {
            LogCiudadanos.evento(EVENTO).campo(RESULTADO, "apagado").escribirEn(LOG);
            return;
        }
        timers.createCalendarTimer(new ScheduleExpression().hour(0).minute(5).second(0)
                .timezone(InfraccionesService.URUGUAY.getId()), new TimerConfig(null, false));
        timers.createSingleActionTimer(ESPERA_INICIAL_MS, new TimerConfig(null, false));
        LogCiudadanos.evento(EVENTO).campo(RESULTADO, "programado").campo("hora", "00:05")
                .campo("zona", InfraccionesService.URUGUAY.getId()).escribirEn(LOG);
    }

    @Timeout
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    void revisar() {
        try {
            if (AdvisoryLock.correr(database, LOCK, this::vencerPlazos).isEmpty()) {
                LogCiudadanos.evento(EVENTO).campo(RESULTADO, "otra_replica").escribirEn(LOG);
            }
        } catch (SQLException | RuntimeException e) {
            LogCiudadanos.aviso(EVENTO).campo(RESULTADO, "error").error(e).escribirEn(LOG);
        }
    }

    // Una revision: devuelve cuantos casos paso a EN_DESCARGO. Cada caso va en su propia transaccion
    // (InfraccionesService.vencerPlazo): si uno falla se sigue con el resto y se reintenta en la proxima.
    int vencerPlazos() {
        List<Long> vencidos = infracciones.casosConPlazoVencido();
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
                LogCiudadanos.aviso(EVENTO).correlationId(correlationId).campo("caso", idCaso)
                        .campo(RESULTADO, "error").error(e).escribirEn(LOG);
            }
        }
        LogCiudadanos.evento(EVENTO).correlationId(correlationId).campo(RESULTADO, "ok")
                .campo("vencidos", vencidos.size()).campo("enDescargo", pasados).campo("fallidos", fallidos)
                .escribirEn(LOG);
        return pasados;
    }

    private static boolean activoConfigurado() {
        String valor = System.getenv("VENCIMIENTO_DESCARGO_ACTIVO");
        return valor == null || !"false".equalsIgnoreCase(valor.trim());
    }
}

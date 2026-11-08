package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes;

import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

import tse.lab.backofficeautoridadesadministradores.service.LogBackoffice;
import tse.lab.log.LogEstructurado;

/**
 * The structured log of a report: one "reporte.consulta" per query the KPIs run, with the JPQL and its parameters
 * as they were executed, under the correlation id and user of the request. The KPIs do not know about requests, so
 * the report sets them for its calculation (within) and the queries read them from here.
 */
public final class ReportLog {

    private static final Logger LOG = Logger.getLogger(ReportLog.class.getName());

    private static final class Context {
        private final String correlationId;
        private final String user;
        private int queries;

        private Context(String correlationId, String user) {
            this.correlationId = correlationId;
            this.user = user;
        }
    }

    // The calculation runs in the thread of the request
    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

    private ReportLog() {
    }

    // Runs the calculation of a report; its queries log under this correlation id and user
    static <T> T within(String correlationId, String user, Supplier<T> calculation) {
        Context previous = CONTEXT.get();
        CONTEXT.set(new Context(correlationId, user));
        try {
            return calculation.get();
        } finally {
            // A pooled thread must not keep the report it ran: removed, unless a report runs inside another one
            if (previous == null) {
                CONTEXT.remove();
            } else {
                CONTEXT.set(previous);
            }
        }
    }

    // How many queries the report running in this thread has made so far
    static int queries() {
        Context context = CONTEXT.get();
        return context == null ? 0 : context.queries;
    }

    // Called by the KPIs right after each query; start is the System.nanoTime() before running it
    public static void query(String kpi, String jpql, Map<String, Object> parameters, int rows, long start) {
        Context context = CONTEXT.get();
        if (context != null) {
            context.queries++;
        }
        LOG.info(() -> event("reporte.consulta", context)
                .roundtripSeconds(LogEstructurado.segundosDesde(start))
                .campo("kpi", kpi)
                .campo("jpql", jpql)
                .campo("parametros", parameters.toString())
                .campo("filas", rows)
                .toString());
    }

    static LogEstructurado event(String name) {
        return event(name, CONTEXT.get());
    }

    private static LogEstructurado event(String name, Context context) {
        LogEstructurado event = LogBackoffice.evento(name);
        return context == null ? event : event.correlationId(context.correlationId).usuario(context.user);
    }
}

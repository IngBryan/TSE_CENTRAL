package tse.lab.log;

import java.util.logging.Logger;

import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

import tse.lab.model.RegistroAuditoriaCaso;

// Pista de auditoria por consola: una linea por cada registro de fiscalizacion (creacion, cambio de estado y
// resolucion de un caso) con quien hizo que accion, con que fundamento y cuando. Solo agrega: no hay forma de
// modificar ni borrar lo escrito desde la aplicacion. La linea se escribe cuando confirma la transaccion que guarda
// el RegistroAuditoriaCaso: una accion deshecha por un rollback no deja rastro. Filtrar por evento=auditoria.caso.
public final class PistaAuditoria {

    private static final Logger LOG = Logger.getLogger("tse.lab.auditoria");

    private PistaAuditoria() {
    }

    public static void alConfirmar(TransactionSynchronizationRegistry transacciones, String componente,
            RegistroAuditoriaCaso r) {
        transacciones.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
                // Nothing to do until the outcome is known
            }

            @Override
            public void afterCompletion(int estado) {
                if (estado == Status.STATUS_COMMITTED) {
                    registrar(componente, r);
                }
            }
        });
    }

    private static void registrar(String componente, RegistroAuditoriaCaso r) {
        LOG.info(() -> LogEstructurado.de(componente, "auditoria.caso")
                .correlationId(r.getCorrelationId())
                .usuario(r.getUsuario())
                .campo("registro", r.getId())
                .campo("caso", r.getIdCaso())
                .campo("accion", r.getAccion())
                .campo("estadoAnterior", r.getEstadoAnterior())
                .campo("estadoNuevo", r.getEstadoNuevo())
                .campo("fundamento", r.getFundamento())
                .campo("fechaHora", r.getFechaHora())
                .toString());
    }
}

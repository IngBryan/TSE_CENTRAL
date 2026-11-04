package tse.lab.frontofficeciudadanos.data;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.TransactionSynchronizationRegistry;
import tse.lab.log.PistaAuditoria;
import tse.lab.model.AccionAuditoriaCaso;
import tse.lab.model.CasoFiscalizacion;
import tse.lab.model.Descargo;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoCaso;
import tse.lab.model.RegistroAuditoriaCaso;

// Casos de fiscalizacion vistos desde la empresa (infracciones y descargos). La empresa de un caso es la
// de su vehiculo.
@Stateless
public class CasoFiscalizacionDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;
    @Resource
    private TransactionSynchronizationRegistry transacciones;

    public List<CasoFiscalizacion> buscarPorEmpresa(Empresa empresa, Collection<EstadoCaso> estados) {
        return em.createQuery("SELECT c FROM CasoFiscalizacion c WHERE c.vehiculo.empresa = :empresa"
                + " AND c.estado IN :estados ORDER BY c.fechaDeteccion DESC, c.id DESC", CasoFiscalizacion.class)
                .setParameter("empresa", empresa)
                .setParameter("estados", estados)
                .getResultList();
    }

    public CasoFiscalizacion buscarPorId(Long id) {
        return em.find(CasoFiscalizacion.class, id);
    }

    // Igual que en Frontofficefuncionarios: se bloquea la fila con lock y se relee con refresh, asi dos
    // descargos (o un descargo y la notificacion) no pisan el estado del caso.
    public CasoFiscalizacion buscarPorIdParaActualizar(Long id) {
        CasoFiscalizacion caso = em.find(CasoFiscalizacion.class, id);
        if (caso != null) {
            em.lock(caso, LockModeType.PESSIMISTIC_WRITE);
            em.refresh(caso);
        }
        return caso;
    }

    public void guardarDescargo(Descargo descargo) {
        em.persist(descargo);
    }

    public void registrarAuditoria(RegistroAuditoriaCaso registro) {
        em.persist(registro);
        PistaAuditoria.alConfirmar(transacciones, "frontofficeciudadanos", registro);
    }

    // Cuando se le notifico el caso a la empresa: el ultimo pasaje a NOTIFICADA de la pista de auditoria
    public Instant fechaNotificacion(Long idCaso) {
        List<Instant> fechas = em.createQuery("SELECT r.fechaHora FROM RegistroAuditoriaCaso r"
                + " WHERE r.idCaso = :idCaso AND r.accion = :accion AND r.estadoNuevo = :notificada"
                + " ORDER BY r.fechaHora DESC", Instant.class)
                .setParameter("idCaso", idCaso)
                .setParameter("accion", AccionAuditoriaCaso.CAMBIO_ESTADO)
                .setParameter("notificada", EstadoCaso.NOTIFICADA)
                .setMaxResults(1)
                .getResultList();
        return fechas.isEmpty() ? null : fechas.get(0);
    }
}

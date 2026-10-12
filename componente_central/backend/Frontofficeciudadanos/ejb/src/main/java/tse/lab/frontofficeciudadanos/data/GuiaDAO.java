package tse.lab.frontofficeciudadanos.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import tse.lab.model.Chofer;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoGuia;
import tse.lab.model.Guia;

@Stateless
public class GuiaDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public Guia buscarPorId(String id) {
        return em.find(Guia.class, id);
    }

    // Bloquea la fila hasta el fin de la transaccion: dos eventos del movil sobre la misma guia
    // (por ejemplo dos INICIO casi simultaneos) se procesan de a uno y el segundo ve el estado nuevo.
    public Guia buscarPorIdParaActualizar(String id) {
        return em.find(Guia.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    public void guardar(Guia guia) {
        em.persist(guia);
    }

    public void sincronizar() {
        em.flush();
    }

    public Guia actualizar(Guia guia) {
        return em.merge(guia);
    }

    public List<Guia> listarTodas() {
        return em.createQuery("SELECT g FROM Guia g", Guia.class)
                .getResultList();
    }

    public List<Guia> buscarPorEmpresa(Empresa empresa) {
        return em.createQuery(
                "SELECT g FROM Guia g WHERE g.empresa = :empresa", Guia.class)
                .setParameter("empresa", empresa)
                .getResultList();
    }

    // Una guia de esa empresa (null si no existe o es de otra empresa)
    public Guia buscarPorIdYEmpresa(String id, Empresa empresa) {
        return em.createQuery("SELECT g FROM Guia g WHERE g.id = :id AND g.empresa = :empresa", Guia.class)
                .setParameter("id", id)
                .setParameter("empresa", empresa)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    public List<Guia> buscarPorEmpresaOrdenadas(Empresa empresa) {
        return em.createQuery(
                "SELECT g FROM Guia g WHERE g.empresa = :empresa ORDER BY g.fecha DESC, g.id", Guia.class)
                .setParameter("empresa", empresa)
                .getResultList();
    }

    // App movil: las guias asignadas a esas asociaciones de chofer en alguno de los estados dados
    public List<Guia> buscarPorChoferesYEstados(List<Chofer> choferes, List<EstadoGuia> estados) {
        if (choferes.isEmpty()) {
            return List.of();
        }
        return em.createQuery(
                "SELECT g FROM Guia g WHERE g.chofer IN :choferes AND g.estado IN :estados ORDER BY g.fecha, g.id",
                Guia.class)
                .setParameter("choferes", choferes)
                .setParameter("estados", estados)
                .getResultList();
    }

    public List<Guia> buscarSinVehiculoAsignado() {
        return em.createQuery(
                "SELECT g FROM Guia g WHERE g.vehiculo IS NULL", Guia.class)
                .getResultList();
    }
}
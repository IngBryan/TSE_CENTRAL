package tse.lab.frontofficeciudadanos.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import tse.lab.model.Chofer;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoChofer;

@Stateless
public class ChoferDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public Chofer buscarPorId(Long id) {
        return em.find(Chofer.class, id);
    }

    // Una persona puede ser chofer de varias empresas: devuelve todas sus asociaciones.
    public List<Chofer> buscarPorCi(String ci) {
        return em.createQuery("SELECT c FROM Chofer c WHERE c.ciudadano.ci = :ci", Chofer.class)
                .setParameter("ci", ci)
                .getResultList();
    }

    public Chofer buscarPorCiYEmpresa(String ci, Empresa empresa) {
        return em.createQuery("SELECT c FROM Chofer c WHERE c.ciudadano.ci = :ci AND c.empresa = :empresa", Chofer.class)
                .setParameter("ci", ci)
                .setParameter("empresa", empresa)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    public void guardar(Chofer chofer) {
        em.persist(chofer);
    }

    public Chofer actualizar(Chofer chofer) {
        return em.merge(chofer);
    }

    public void eliminar(Chofer chofer) {
        em.remove(em.contains(chofer) ? chofer : em.merge(chofer));
    }

    public List<Chofer> buscarPorEmpresa(Empresa empresa) {
        return em.createQuery(
                "SELECT c FROM Chofer c WHERE c.empresa = :empresa", Chofer.class)
                .setParameter("empresa", empresa)
                .getResultList();
    }

    // CU-02 FA5 6c: cuando la persona queda validada se habilitan sus asociaciones pendientes.
    public List<Chofer> habilitarPendientes(String ci) {
        List<Chofer> pendientes = em.createQuery(
                "SELECT c FROM Chofer c WHERE c.ciudadano.ci = :ci AND c.estado = :pendiente", Chofer.class)
                .setParameter("ci", ci)
                .setParameter("pendiente", EstadoChofer.PENDIENTE_VALIDACION)
                .getResultList();
        pendientes.forEach(c -> c.setEstado(EstadoChofer.HABILITADO));
        return pendientes;
    }
}

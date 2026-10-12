package tse.lab.frontofficeciudadanos.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import tse.lab.frontofficeciudadanos.model.Chofer;
import tse.lab.frontofficeciudadanos.model.Empresa;

@Stateless
public class ChoferDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public Chofer buscarPorCi(String ci) {
        return em.find(Chofer.class, ci);
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
}
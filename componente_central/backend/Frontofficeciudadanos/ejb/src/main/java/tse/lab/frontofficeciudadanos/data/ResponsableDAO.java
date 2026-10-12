package tse.lab.frontofficeciudadanos.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Responsable;

@Stateless
public class ResponsableDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public Responsable buscarPorCi(String ci) {
        return em.find(Responsable.class, ci);
    }

    public void guardar(Responsable responsable) {
        em.persist(responsable);
    }

    public Responsable actualizar(Responsable responsable) {
        return em.merge(responsable);
    }
}
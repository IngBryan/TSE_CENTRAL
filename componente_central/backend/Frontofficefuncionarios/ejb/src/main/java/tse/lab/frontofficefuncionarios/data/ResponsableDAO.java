package tse.lab.frontofficefuncionarios.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.frontofficefuncionarios.model.Responsable;

@Stateless
public class ResponsableDAO {

    @PersistenceContext(unitName = "frontofficefuncionariosPersistenceUnit")
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

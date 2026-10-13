package tse.lab.frontofficeciudadanos.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Guia;
import tse.lab.model.ModificacionGuia;

@Stateless
public class ModificacionGuiaDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public void guardar(ModificacionGuia modificacion) {
        em.persist(modificacion);
    }

    public List<ModificacionGuia> buscarPorGuia(Guia guia) {
        return em.createQuery("SELECT m FROM ModificacionGuia m WHERE m.guia = :guia ORDER BY m.fecha, m.id",
                ModificacionGuia.class)
                .setParameter("guia", guia)
                .getResultList();
    }
}

package tse.lab.frontofficefuncionarios.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Guia;
import tse.lab.model.ModificacionGuia;

// Solo lectura: las modificaciones las registra el responsable en Frontofficeciudadanos
@Stateless
public class ModificacionGuiaDAO {

    @PersistenceContext(unitName = "frontofficefuncionariosPersistenceUnit")
    private EntityManager em;

    public List<ModificacionGuia> buscarPorGuia(Guia guia) {
        return em.createQuery("SELECT m FROM ModificacionGuia m WHERE m.guia = :guia ORDER BY m.fecha, m.id",
                ModificacionGuia.class)
                .setParameter("guia", guia)
                .getResultList();
    }
}

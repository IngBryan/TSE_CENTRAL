package tse.lab.frontofficefuncionarios.data;

import java.util.Collection;
import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Rubro;

// Catalogo precargado de rubros de empresa (solo lectura: no tiene ABM)
@Stateless
public class RubroDAO {

    @PersistenceContext(unitName = "frontofficefuncionariosPersistenceUnit")
    private EntityManager em;

    public List<Rubro> listar() {
        return em.createQuery("SELECT r FROM Rubro r ORDER BY r.nombre", Rubro.class).getResultList();
    }

    public List<Rubro> buscarPorIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return em.createQuery("SELECT r FROM Rubro r WHERE r.id IN :ids ORDER BY r.nombre", Rubro.class)
                .setParameter("ids", ids).getResultList();
    }
}

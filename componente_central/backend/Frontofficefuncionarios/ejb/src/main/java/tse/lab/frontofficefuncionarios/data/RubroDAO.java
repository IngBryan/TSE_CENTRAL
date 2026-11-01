package tse.lab.frontofficefuncionarios.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Rubro;

// Catalogo precargado de rubros (solo lectura)
@Stateless
public class RubroDAO {

    @PersistenceContext(unitName = "frontofficefuncionariosPersistenceUnit")
    private EntityManager em;

    public List<String> listar() {
        return em.createQuery("SELECT r.nombre FROM Rubro r ORDER BY r.nombre", String.class).getResultList();
    }

    public boolean existe(String nombre) {
        return em.find(Rubro.class, nombre) != null;
    }
}

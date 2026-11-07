package tse.lab.frontofficeciudadanos.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Rubro;

// Catalogo precargado de rubros (solo lectura: no tiene ABM). En las guias es el rubro del cliente.
@Stateless
public class RubroDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public List<Rubro> listar() {
        return em.createQuery("SELECT r FROM Rubro r ORDER BY r.nombre", Rubro.class).getResultList();
    }
}

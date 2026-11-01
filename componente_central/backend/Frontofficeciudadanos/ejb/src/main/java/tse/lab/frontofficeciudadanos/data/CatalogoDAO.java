package tse.lab.frontofficeciudadanos.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Rubro;
import tse.lab.model.TipoCarga;

// Catalogos precargados de rubros y tipos de carga (solo lectura)
@Stateless
public class CatalogoDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public List<String> rubros() {
        return em.createQuery("SELECT r.nombre FROM Rubro r ORDER BY r.nombre", String.class).getResultList();
    }

    public List<String> tiposCarga() {
        return em.createQuery("SELECT t.nombre FROM TipoCarga t ORDER BY t.nombre", String.class).getResultList();
    }

    public boolean existeRubro(String nombre) {
        return em.find(Rubro.class, nombre) != null;
    }

    public boolean existeTipoCarga(String nombre) {
        return em.find(TipoCarga.class, nombre) != null;
    }
}

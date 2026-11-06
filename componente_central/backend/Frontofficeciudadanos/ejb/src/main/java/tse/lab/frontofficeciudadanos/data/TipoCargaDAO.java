package tse.lab.frontofficeciudadanos.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.TipoCarga;

// Catalogo precargado de tipos de carga de las guias (solo lectura: no tiene ABM)
@Stateless
public class TipoCargaDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public List<TipoCarga> listar() {
        return em.createQuery("SELECT t FROM TipoCarga t ORDER BY t.nombre", TipoCarga.class).getResultList();
    }

    // El del catalogo con ese nombre, sin distinguir mayusculas; null si no esta
    public TipoCarga buscarPorNombre(String nombre) {
        return em.createQuery("SELECT t FROM TipoCarga t WHERE LOWER(t.nombre) = LOWER(:nombre)", TipoCarga.class)
                .setParameter("nombre", nombre).getResultStream().findFirst().orElse(null);
    }
}

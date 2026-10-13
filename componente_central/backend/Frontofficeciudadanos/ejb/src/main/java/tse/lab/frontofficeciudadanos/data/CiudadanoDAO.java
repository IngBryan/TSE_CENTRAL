package tse.lab.frontofficeciudadanos.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import tse.lab.model.EstadoValidacion;
import tse.lab.model.Ciudadano;

@Stateless
public class CiudadanoDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public Ciudadano buscarPorCi(String ci) {
        return em.find(Ciudadano.class, ci);
    }

    public void guardar(Ciudadano perfil) {
        em.persist(perfil);
    }

    public Ciudadano actualizar(Ciudadano perfil) {
        return em.merge(perfil);
    }

    // Para CU-04: los perfiles que quedaron pendientes porque la PDI no respondio.
    public List<Ciudadano> buscarPorEstado(EstadoValidacion estado) {
        return em.createQuery(
                "SELECT p FROM Ciudadano p WHERE p.estado = :estado", Ciudadano.class)
                .setParameter("estado", estado)
                .getResultList();
    }
}

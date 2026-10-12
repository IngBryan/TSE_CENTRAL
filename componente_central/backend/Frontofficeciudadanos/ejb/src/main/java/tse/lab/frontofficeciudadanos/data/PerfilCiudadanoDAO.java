package tse.lab.frontofficeciudadanos.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import tse.lab.model.EstadoValidacion;
import tse.lab.model.PerfilCiudadano;

@Stateless
public class PerfilCiudadanoDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public PerfilCiudadano buscarPorCi(String ci) {
        return em.find(PerfilCiudadano.class, ci);
    }

    public void guardar(PerfilCiudadano perfil) {
        em.persist(perfil);
    }

    public PerfilCiudadano actualizar(PerfilCiudadano perfil) {
        return em.merge(perfil);
    }

    // Para CU-04: los perfiles que quedaron pendientes porque la PDI no respondio.
    public List<PerfilCiudadano> buscarPorEstado(EstadoValidacion estado) {
        return em.createQuery(
                "SELECT p FROM PerfilCiudadano p WHERE p.estado = :estado", PerfilCiudadano.class)
                .setParameter("estado", estado)
                .getResultList();
    }
}

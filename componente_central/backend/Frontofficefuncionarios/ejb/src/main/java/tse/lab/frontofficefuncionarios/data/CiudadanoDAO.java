package tse.lab.frontofficefuncionarios.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Ciudadano;

@Stateless
public class CiudadanoDAO {

    @PersistenceContext(unitName = "frontofficefuncionariosPersistenceUnit")
    private EntityManager em;

    // Solo lectura: los ciudadanos los crea y valida Frontofficeciudadanos
    public Ciudadano buscarPorCi(String ci) {
        return em.find(Ciudadano.class, ci);
    }
}

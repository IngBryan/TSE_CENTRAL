package tse.lab.frontofficefuncionarios.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Ciudadano;
import tse.lab.model.EstadoValidacion;

@Stateless
public class CiudadanoDAO {

    @PersistenceContext(unitName = "frontofficefuncionariosPersistenceUnit")
    private EntityManager em;

    // Solo lectura: los ciudadanos los crea y valida Frontofficeciudadanos
    public Ciudadano buscarPorCi(String ci) {
        return em.find(Ciudadano.class, ci);
    }

    // Validados, cuya cedula empieza con prefijo y que no son responsables de ninguna empresa
    public List<Ciudadano> disponiblesComoResponsable(String prefijo, int maximo) {
        return em.createQuery("SELECT c FROM Ciudadano c WHERE c.estado = :validado AND c.ci LIKE :prefijo"
                + " AND NOT EXISTS (SELECT r FROM Responsable r WHERE r.ci = c.ci) ORDER BY c.ci", Ciudadano.class)
                .setParameter("validado", EstadoValidacion.VALIDADO)
                .setParameter("prefijo", prefijo + "%")
                .setMaxResults(maximo)
                .getResultList();
    }
}

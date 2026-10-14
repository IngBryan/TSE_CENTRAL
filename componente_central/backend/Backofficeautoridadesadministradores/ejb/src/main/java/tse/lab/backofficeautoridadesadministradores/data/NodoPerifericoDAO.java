package tse.lab.backofficeautoridadesadministradores.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import tse.lab.model.NodoPeriferico;

@Stateless
public class NodoPerifericoDAO {

    @PersistenceContext(unitName = "backofficeautoridadesadministradoresPersistenceUnit")
    private EntityManager em;

    // Consulta polimorfica: devuelve NodoBalanza y NodoTracking
    public List<NodoPeriferico> listarActivos() {
        return em.createQuery("SELECT n FROM NodoPeriferico n WHERE n.activo = true ORDER BY n.id",
                NodoPeriferico.class)
                .getResultList();
    }
}

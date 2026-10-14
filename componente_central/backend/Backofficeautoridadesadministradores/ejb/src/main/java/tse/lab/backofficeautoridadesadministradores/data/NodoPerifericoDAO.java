
package tse.lab.backofficeautoridadesadministradores.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tse.lab.model.NodoPeriferico;

@Stateless
public class NodoPerifericoDAO {

    @PersistenceContext(
        unitName = "backofficeautoridadesadministradoresPersistenceUnit"
    )
    private EntityManager em;

    // Registrar un nuevo nodo periférico.
    public void guardar(NodoPeriferico nodo) {
        em.persist(nodo);
    }

    // Buscar un nodo por su identificador interno.
    public NodoPeriferico buscarPorId(Long id) {
        return em.find(NodoPeriferico.class, id);
    }

    // Obtener todos los nodos periféricos.
    public List<NodoPeriferico> listarTodos() {
        return em.createQuery(
            "SELECT n FROM NodoPeriferico n ORDER BY n.id",
            NodoPeriferico.class
        ).getResultList();
    }

    // Obtener únicamente los nodos activos.
    public List<NodoPeriferico> listarActivos() {
        return em.createQuery(
            "SELECT n FROM NodoPeriferico n WHERE n.activo = true ORDER BY n.id",
            NodoPeriferico.class
        ).getResultList();
    }

    // Verificar si existe otro nodo del mismo tipo
    // con el identificador externo indicado.
    public boolean existePorTipoEIdExterno(
            Class<? extends NodoPeriferico> subtipo,
            String idExterno) {

        Long cantidad = em.createQuery(
            """
            SELECT COUNT(n)
            FROM NodoPeriferico n
            WHERE TYPE(n) = :subtipo
              AND n.idExterno = :idExterno
            """,
            Long.class
        )
        .setParameter("subtipo", subtipo)
        .setParameter("idExterno", idExterno)
        .getSingleResult();

        return cantidad > 0;
    }
}

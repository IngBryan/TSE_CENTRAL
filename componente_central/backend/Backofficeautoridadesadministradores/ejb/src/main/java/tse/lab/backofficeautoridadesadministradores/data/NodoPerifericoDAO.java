
package tse.lab.backofficeautoridadesadministradores.data;

import java.time.Instant;
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
        em.flush();
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
            Long idExterno) {

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

    // Avanza la ultima comunicacion exitosa del nodo (tipo = BALANZA o TRACKING, el discriminador). Nunca la
    // retrocede: la fiscalizacion la manda en lotes desde varios pods, que pueden llegar desordenados.
    public int registrarComunicacion(String tipo, Long idExterno, Instant fecha) {
        return em.createNativeQuery(
            """
            UPDATE nodos_perifericos
            SET ultima_comunicacion_exitosa = ?1
            WHERE tipo = ?2
              AND id_externo = ?3
              AND (ultima_comunicacion_exitosa IS NULL OR ultima_comunicacion_exitosa < ?1)
            """
        )
        .setParameter(1, fecha)
        .setParameter(2, tipo)
        .setParameter(3, idExterno)
        .executeUpdate();
    }
}

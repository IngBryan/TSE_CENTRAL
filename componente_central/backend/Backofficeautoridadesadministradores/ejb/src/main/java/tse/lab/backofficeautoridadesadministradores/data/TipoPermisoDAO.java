package tse.lab.backofficeautoridadesadministradores.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tse.lab.model.TipoPermiso;

@Stateless
public class TipoPermisoDAO {

    @PersistenceContext(
        unitName = "backofficeautoridadesadministradoresPersistenceUnit"
    )
    private EntityManager em;

    public void guardar(TipoPermiso tipo) {
        em.persist(tipo);
        em.flush();
    }

    public TipoPermiso buscarPorId(Long id) {
        return em.find(TipoPermiso.class, id);
    }

    public List<TipoPermiso> listarTodos() {
        return em.createQuery(
            "SELECT t FROM TipoPermiso t ORDER BY t.nombre",
            TipoPermiso.class
        ).getResultList();
    }

    // Otro tipo con ese nombre, sin distinguir mayusculas. idExcluido: el que se esta modificando.
    public boolean existeNombre(String nombre, Long idExcluido) {
        var consulta = em.createQuery(
            "SELECT COUNT(t) FROM TipoPermiso t WHERE LOWER(t.nombre) = LOWER(:nombre)"
                + (idExcluido == null ? "" : " AND t.id <> :id"),
            Long.class
        ).setParameter("nombre", nombre);

        if (idExcluido != null) {
            consulta.setParameter("id", idExcluido);
        }

        return consulta.getSingleResult() > 0;
    }
}

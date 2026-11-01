package tse.lab.frontofficefuncionarios.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.TipoPermiso;

// Tipos de permiso a demanda; los administra el backoffice
@Stateless
public class TipoPermisoDAO {

    @PersistenceContext(unitName = "frontofficefuncionariosPersistenceUnit")
    private EntityManager em;

    public List<TipoPermiso> listarActivos() {
        return em.createQuery("SELECT t FROM TipoPermiso t WHERE t.activo = true ORDER BY t.nombre", TipoPermiso.class)
                .getResultList();
    }
}

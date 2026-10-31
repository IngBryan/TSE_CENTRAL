package tse.lab.frontofficeciudadanos.data;

import java.util.List;
import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Localidad;

/** Catalogo de localidades para el futuro formulario y el servicio de rutas. */
@Stateless
public class LocalidadDAO {
    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public Localidad buscarPorId(Long id) {
        return id == null ? null : em.find(Localidad.class, id);
    }

    public List<Localidad> listar() {
        return em.createQuery("SELECT l FROM Localidad l ORDER BY l.departamento, l.nombre", Localidad.class)
                .getResultList();
    }
}

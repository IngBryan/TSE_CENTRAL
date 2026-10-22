package tse.lab.frontofficeciudadanos.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.PermisoCirculacion;
import tse.lab.model.Vehiculo;

@Stateless
public class PermisoCirculacionDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public PermisoCirculacion buscarPorNro(String nro) {
        return em.find(PermisoCirculacion.class, nro);
    }

    // Un vehiculo tiene a lo sumo un permiso.
    public PermisoCirculacion buscarPorVehiculo(Vehiculo vehiculo) {
        return em.createQuery("SELECT p FROM PermisoCirculacion p WHERE p.vehiculo = :vehiculo", PermisoCirculacion.class)
                .setParameter("vehiculo", vehiculo)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    public void guardar(PermisoCirculacion permiso) {
        em.persist(permiso);
    }

    // El nro es la clave: para cambiarlo se borra el permiso y se guarda uno nuevo. flush para que el
    // INSERT siguiente no choque con la restriccion unica de la matricula.
    public void eliminar(PermisoCirculacion permiso) {
        em.remove(em.contains(permiso) ? permiso : em.merge(permiso));
        em.flush();
    }
}

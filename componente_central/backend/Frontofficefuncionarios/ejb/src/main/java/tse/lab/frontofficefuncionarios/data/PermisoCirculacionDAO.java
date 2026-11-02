package tse.lab.frontofficefuncionarios.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Empresa;
import tse.lab.model.PermisoCirculacion;
import tse.lab.model.Vehiculo;

@Stateless
public class PermisoCirculacionDAO {

    @PersistenceContext(unitName = "frontofficefuncionariosPersistenceUnit")
    private EntityManager em;

    // Un vehiculo tiene a lo sumo un permiso
    public PermisoCirculacion buscarPorVehiculo(Vehiculo vehiculo) {
        return em.createQuery("SELECT p FROM PermisoCirculacion p WHERE p.vehiculo = :vehiculo",
                PermisoCirculacion.class)
                .setParameter("vehiculo", vehiculo)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    // Los permisos de todos los vehiculos de la empresa en una sola consulta (no una por vehiculo)
    public List<PermisoCirculacion> buscarPorEmpresa(Empresa empresa) {
        return em.createQuery("SELECT p FROM PermisoCirculacion p WHERE p.vehiculo.empresa = :empresa",
                PermisoCirculacion.class)
                .setParameter("empresa", empresa)
                .getResultList();
    }
}

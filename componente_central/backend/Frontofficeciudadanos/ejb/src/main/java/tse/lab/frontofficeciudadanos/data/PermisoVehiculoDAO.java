package tse.lab.frontofficeciudadanos.data;

import java.time.LocalDate;
import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Empresa;
import tse.lab.model.PermisoVehiculo;
import tse.lab.model.TipoPermiso;
import tse.lab.model.Vehiculo;

// Permisos a demanda: los tipos que un funcionario le asigno a la empresa y lo cargado en cada vehiculo
@Stateless
public class PermisoVehiculoDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public void guardar(PermisoVehiculo permiso) {
        em.persist(permiso);
    }

    // Un tipo desactivado por el administrador deja de exigirse
    public List<TipoPermiso> tiposRequeridos(Empresa empresa) {
        return em.createQuery("SELECT t FROM Empresa e JOIN e.permisosRequeridos t"
                + " WHERE e = :empresa AND t.activo = true ORDER BY t.nombre", TipoPermiso.class)
                .setParameter("empresa", empresa)
                .getResultList();
    }

    public List<PermisoVehiculo> buscarPorVehiculo(Vehiculo vehiculo) {
        return em.createQuery("SELECT p FROM PermisoVehiculo p JOIN FETCH p.tipo WHERE p.vehiculo = :vehiculo",
                PermisoVehiculo.class)
                .setParameter("vehiculo", vehiculo)
                .getResultList();
    }

    // Tiene vigentes a la fecha todos los permisos a demanda que se le exigen a su empresa
    public boolean tieneRequeridosVigentes(Vehiculo vehiculo, LocalDate fecha) {
        List<PermisoVehiculo> cargados = buscarPorVehiculo(vehiculo);
        return tiposRequeridos(vehiculo.getEmpresa()).stream().allMatch(t -> cargados.stream()
                .anyMatch(p -> p.getTipo().getId().equals(t.getId()) && p.vigenteAl(fecha)));
    }
}

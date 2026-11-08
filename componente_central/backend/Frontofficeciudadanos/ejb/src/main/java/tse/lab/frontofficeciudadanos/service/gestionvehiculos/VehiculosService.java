package tse.lab.frontofficeciudadanos.service.gestionvehiculos;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.VehiculoDAO;
import tse.lab.frontofficeciudadanos.data.PermisoCirculacionDAO;
import tse.lab.frontofficeciudadanos.data.ITVDAO;
import tse.lab.model.EvidenciaHabilitacion;
import tse.lab.model.Vehiculo;
import java.time.Instant;
import java.util.Objects;

// Datos de un vehiculo para la fiscalizacion automatica: si no existe en el central, no se crean casos.
@Stateless
public class VehiculosService implements VehiculosRemote {

    @EJB
    private VehiculoDAO vehiculos;
    @EJB
    private PermisoCirculacionDAO permisos;
    @EJB
    private ITVDAO itvs;

    @Override
    public DatosVehiculo buscarVehiculo(String matricula, Instant fechaHora) {
        Objects.requireNonNull(fechaHora, "Falta la fecha del evento");
        DatosVehiculo datos = buscarVehiculo(matricula);
        if (datos == null) return null;
        Vehiculo v = vehiculos.buscarPorMatricula(datos.getMatricula());
        return new DatosVehiculo(datos.getMatricula(), datos.getNroEmpresa(), datos.getPesoKg(),
                datos.getCapacidadCargaKg(), EvidenciaHabilitacion.evaluar(v.getMatricula(), fechaHora,
                        permisos.buscarPorVehiculo(v), itvs.buscarPorVehiculo(v)));
    }

    @Override
    public DatosVehiculo buscarVehiculo(String matricula) {
        if (matricula == null || matricula.isBlank()) {
            return null;
        }
        Vehiculo v = vehiculos.buscarPorMatricula(matricula.strip());
        if (v == null) {
            return null;
        }
        Long nroEmpresa = v.getEmpresa() == null ? null : v.getEmpresa().getNroEmpresa();
        return new DatosVehiculo(v.getMatricula(), nroEmpresa, v.getPeso(), v.getCapacidadCargaKg());
    }
}

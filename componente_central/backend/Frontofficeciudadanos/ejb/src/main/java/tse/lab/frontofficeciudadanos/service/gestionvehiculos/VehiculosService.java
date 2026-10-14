package tse.lab.frontofficeciudadanos.service.gestionvehiculos;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.VehiculoDAO;
import tse.lab.model.Vehiculo;

// Datos de un vehiculo para la fiscalizacion automatica: si no existe en el central, no se crean casos.
@Stateless
public class VehiculosService implements VehiculosRemote {

    @EJB
    private VehiculoDAO vehiculos;

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
        return new DatosVehiculo(v.getMatricula(), nroEmpresa);
    }
}

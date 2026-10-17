package tse.lab.frontofficeciudadanos.service.gestionvehiculos;

// Operaciones de la vista remota (VehiculosRemote).
public interface Vehiculos {

    // null si no hay un vehiculo con esa matricula
    DatosVehiculo buscarVehiculo(String matricula);
}

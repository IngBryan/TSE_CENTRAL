package tse.lab.frontofficeciudadanos.service.gestionvehiculos;

// Operaciones de la vista remota (VehiculosRemote).
public interface Vehiculos {

    // null si no hay un vehiculo con esa matricula
    DatosVehiculo buscarVehiculo(String matricula);

    /** Incluye la evaluación de permiso e ITV a la fecha de medición (Uruguay). */
    DatosVehiculo buscarVehiculo(String matricula, java.time.Instant fechaHora);
}

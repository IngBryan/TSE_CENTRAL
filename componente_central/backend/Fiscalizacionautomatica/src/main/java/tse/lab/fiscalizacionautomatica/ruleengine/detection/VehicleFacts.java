package tse.lab.fiscalizacionautomatica.ruleengine.detection;

import java.util.List;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.DatosViaje;
import tse.lab.frontofficeciudadanos.service.gestionvehiculos.DatosVehiculo;

/**
 * What the central knows about a vehicle when one of its events is evaluated.
 *
 * @param activeTrips guides ASIGNADA or EN_CURSO of the vehicle
 */
public record VehicleFacts(DatosVehiculo vehicle, List<DatosViaje> activeTrips) {

    public boolean hasActiveTrip() {
        return !activeTrips.isEmpty();
    }
}

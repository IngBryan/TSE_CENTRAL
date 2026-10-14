package tse.lab.frontofficeciudadanos.service.gestionvehiculos;

import jakarta.ejb.Remote;

// Vista remota: la usa Fiscalizacionautomatica (fuera de WildFly); los datos viajan por valor (DatosVehiculo).
@Remote
public interface VehiculosRemote extends Vehiculos {

    // Nombre para un cliente remoto (wildfly-client-all) con PROVIDER_URL remote+http://<host>:8080
    String LOOKUP = "ejb:frontofficeciudadanos/frontofficeciudadanos-ejb/VehiculosService!"
            + "tse.lab.frontofficeciudadanos.service.gestionvehiculos.VehiculosRemote";
}

package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import jakarta.ejb.Remote;

// Vista remota: la usa el poller de Fiscalizacionautomatica (fuera de WildFly); los datos viajan por valor.
@Remote
public interface PesajesRemote extends Pesajes {

    // Nombre para un cliente remoto (wildfly-client-all) con PROVIDER_URL remote+http://<host>:8080
    String LOOKUP = "ejb:frontofficeciudadanos/frontofficeciudadanos-ejb/PesajesService!"
            + "tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.PesajesRemote";
}

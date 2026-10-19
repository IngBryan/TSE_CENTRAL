package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import jakarta.ejb.Remote;

// Vista remota: la usa Fiscalizacionautomatica (fuera de WildFly); los datos viajan por valor (DatosViaje).
@Remote
public interface ViajesEnCursoRemote extends ViajesEnCurso {

    // Nombre para un cliente remoto (wildfly-client-all) con PROVIDER_URL remote+http://<host>:8080
    String LOOKUP = "ejb:frontofficeciudadanos/frontofficeciudadanos-ejb/ViajesEnCursoService!"
            + "tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.ViajesEnCursoRemote";
}

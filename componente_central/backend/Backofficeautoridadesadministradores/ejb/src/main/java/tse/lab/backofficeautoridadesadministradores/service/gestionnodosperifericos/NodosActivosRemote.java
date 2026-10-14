package tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos;

import jakarta.ejb.Remote;

// Vista remota: la usa Fiscalizacionautomatica (fuera de WildFly); los datos viajan por valor (DatosNodo).
@Remote
public interface NodosActivosRemote extends NodosActivos {

    // Nombre para un cliente remoto (wildfly-client-all) con PROVIDER_URL remote+http://<host>:8080
    String LOOKUP = "ejb:backofficeautoridadesadministradores/backofficeautoridadesadministradores-ejb/"
            + "NodosActivosService!"
            + "tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.NodosActivosRemote";
}

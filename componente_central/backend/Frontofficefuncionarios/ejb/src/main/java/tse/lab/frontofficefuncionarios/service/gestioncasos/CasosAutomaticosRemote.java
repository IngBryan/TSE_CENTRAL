package tse.lab.frontofficefuncionarios.service.gestioncasos;

import jakarta.ejb.Remote;

// Vista remota: la usa Fiscalizacionautomatica (fuera de WildFly); los datos viajan por valor.
@Remote
public interface CasosAutomaticosRemote extends CasosAutomaticos {

    // Nombre para un cliente remoto (wildfly-client-all) con PROVIDER_URL remote+http://<host>:8080
    String LOOKUP = "ejb:frontofficefuncionarios/frontofficefuncionarios-ejb/CasosAutomaticosService!"
            + "tse.lab.frontofficefuncionarios.service.gestioncasos.CasosAutomaticosRemote";
}

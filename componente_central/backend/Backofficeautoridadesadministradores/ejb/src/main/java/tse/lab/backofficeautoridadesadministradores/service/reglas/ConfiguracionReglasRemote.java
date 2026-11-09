package tse.lab.backofficeautoridadesadministradores.service.reglas;

import jakarta.ejb.Remote;
import tse.lab.reglas.ConfiguracionReglas;

@Remote
public interface ConfiguracionReglasRemote {
    String LOOKUP="ejb:backofficeautoridadesadministradores/backofficeautoridadesadministradores-ejb/"
            + "ConsultaReglasService!tse.lab.backofficeautoridadesadministradores.service.reglas.ConfiguracionReglasRemote";
    ConfiguracionReglas obtenerVigente();
    ConfiguracionReglas obtenerRevision(long revision);
}

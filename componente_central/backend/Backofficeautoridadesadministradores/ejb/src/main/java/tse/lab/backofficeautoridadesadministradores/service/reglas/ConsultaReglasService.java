package tse.lab.backofficeautoridadesadministradores.service.reglas;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.backofficeautoridadesadministradores.data.ReglasDAO;
import tse.lab.reglas.ConfiguracionReglas;

/** Lectura para procesos internos autenticados por ApplicationRealm, como NodosActivosRemote. */
@Stateless
public class ConsultaReglasService implements ConfiguracionReglasRemote {
    @EJB private ReglasDAO reglas;
    public ConfiguracionReglas obtenerVigente() { return reglas.revision(reglas.vigente(false).getRevision()).datos(); }
    public ConfiguracionReglas obtenerRevision(long revision) { return reglas.revision(revision).datos(); }
}

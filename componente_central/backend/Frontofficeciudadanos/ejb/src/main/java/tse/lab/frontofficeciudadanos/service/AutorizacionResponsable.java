package tse.lab.frontofficeciudadanos.service;

import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.EmpresaDAO;
import tse.lab.frontofficeciudadanos.data.PerfilCiudadanoDAO;
import tse.lab.frontofficeciudadanos.model.Empresa;
import tse.lab.frontofficeciudadanos.model.EstadoValidacion;
import tse.lab.frontofficeciudadanos.model.PerfilCiudadano;
import tse.lab.frontofficefuncionarios.api.gestiondeempresas.DatosResponsable;
import tse.lab.frontofficefuncionarios.api.gestiondeempresas.ResponsablesRemote;

// Control de acceso de las funcionalidades del responsable de la empresa (capa de servicios, no la UI):
// la persona tiene que ser responsable de una empresa y tener su perfil validado; algunas operaciones
// (como el alta de choferes) exigen ademas que la empresa este habilitada. Los responsables viven en
// Frontofficefuncionarios (EJB remoto); la empresa y el perfil, aca.
@Stateless
public class AutorizacionResponsable {

    private static final Logger LOG = Logger.getLogger(AutorizacionResponsable.class.getName());

    @EJB(lookup = ResponsablesRemote.JNDI)
    private ResponsablesRemote responsables;
    @EJB
    private EmpresaDAO empresas;
    @EJB
    private PerfilCiudadanoDAO perfiles;

    public Empresa empresaDelResponsable(String ciResponsable, boolean exigirHabilitada, String correlationId)
            throws NoAutorizadoException {
        DatosResponsable responsable = ciResponsable == null ? null : responsables.buscarPorCi(ciResponsable);
        Empresa empresa = responsable == null || responsable.nroEmpresa() == null ? null
                : empresas.buscarPorId(responsable.nroEmpresa());
        String motivo = null;
        if (empresa == null) {
            motivo = "no es responsable de ninguna empresa";
        } else if (exigirHabilitada && !empresa.isHabilitada()) {
            motivo = "la empresa no esta habilitada";
        } else {
            PerfilCiudadano perfil = perfiles.buscarPorCi(ciResponsable);
            if (perfil == null || perfil.getEstado() != EstadoValidacion.VALIDADO) {
                motivo = "el perfil del responsable no esta validado";
            }
        }
        if (motivo != null) {
            final String m = motivo;
            LOG.warning(() -> "evento=responsable.no_autorizado correlationId=" + correlationId + " usuario="
                    + ciResponsable + " detalle=\"" + m + "\"");
            throw new NoAutorizadoException(motivo);
        }
        return empresa;
    }
}

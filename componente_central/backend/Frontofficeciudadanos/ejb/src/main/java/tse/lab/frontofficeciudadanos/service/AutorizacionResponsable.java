package tse.lab.frontofficeciudadanos.service;

import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.PerfilCiudadanoDAO;
import tse.lab.frontofficeciudadanos.data.ResponsableDAO;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoValidacion;
import tse.lab.model.PerfilCiudadano;
import tse.lab.model.Responsable;

// Control de acceso de las funcionalidades del responsable de la empresa (capa de servicios, no la UI):
// la persona tiene que ser responsable de una empresa y tener su perfil validado; algunas operaciones
// (como el alta de choferes) exigen ademas que la empresa este habilitada.
@Stateless
public class AutorizacionResponsable {

    private static final Logger LOG = Logger.getLogger(AutorizacionResponsable.class.getName());

    @EJB
    private ResponsableDAO responsables;
    @EJB
    private PerfilCiudadanoDAO perfiles;

    public Empresa empresaDelResponsable(String ciResponsable, boolean exigirHabilitada, String correlationId)
            throws NoAutorizadoException {
        Responsable responsable = ciResponsable == null ? null : responsables.buscarPorCi(ciResponsable);
        String motivo = null;
        if (responsable == null || responsable.getEmpresa() == null) {
            motivo = "no es responsable de ninguna empresa";
        } else if (exigirHabilitada && !responsable.getEmpresa().isHabilitada()) {
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
        return responsable.getEmpresa();
    }
}

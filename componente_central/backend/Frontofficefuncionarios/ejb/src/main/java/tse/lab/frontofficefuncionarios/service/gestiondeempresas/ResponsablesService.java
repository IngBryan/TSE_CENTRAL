package tse.lab.frontofficefuncionarios.service.gestiondeempresas;

import jakarta.ejb.EJB;
import jakarta.ejb.Remote;
import jakarta.ejb.Stateless;
import tse.lab.frontofficefuncionarios.api.gestiondeempresas.DatosResponsable;
import tse.lab.frontofficefuncionarios.api.gestiondeempresas.ResponsablesRemote;
import tse.lab.frontofficefuncionarios.data.ResponsableDAO;
import tse.lab.frontofficefuncionarios.model.Responsable;

// Responsables de empresa. Frontofficeciudadanos los consulta por EJB remoto para los roles del
// login (CU-01) y para autorizar las funcionalidades del responsable (CU-02, CU-07, perfil de empresa).
@Stateless
@Remote(ResponsablesRemote.class)
public class ResponsablesService implements ResponsablesRemote {

    @EJB
    private ResponsableDAO responsables;

    @Override
    public DatosResponsable buscarPorCi(String ci) {
        Responsable r = ci == null ? null : responsables.buscarPorCi(ci);
        return r == null ? null : new DatosResponsable(r.getCi(), r.getEmail(), r.getNroEmpresa());
    }

    @Override
    public void completarEmail(String ci, String email) {
        Responsable r = ci == null ? null : responsables.buscarPorCi(ci);
        if (r != null && (r.getEmail() == null || r.getEmail().isBlank()) && email != null) {
            r.setEmail(email); // entidad administrada: se guarda al cerrar la transaccion
        }
    }
}

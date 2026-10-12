package tse.lab.frontofficeciudadanos.service.autenticacion;

import java.util.ArrayList;
import java.util.List;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.ChoferDAO;
import tse.lab.frontofficeciudadanos.data.EmpresaDAO;
import tse.lab.frontofficeciudadanos.model.Chofer;
import tse.lab.frontofficeciudadanos.model.Empresa;
import tse.lab.frontofficefuncionarios.api.gestiondeempresas.DatosResponsable;
import tse.lab.frontofficefuncionarios.api.gestiondeempresas.ResponsablesRemote;

// Roles de una persona en carga.uy: responsable de una empresa y/o chofer de una o varias.
@Stateless
public class RolesService {

    @EJB(lookup = ResponsablesRemote.JNDI)
    private ResponsablesRemote responsables;
    @EJB
    private EmpresaDAO empresas;
    @EJB
    private ChoferDAO choferes;

    public List<RolPersona> rolesDe(String ci) {
        List<RolPersona> roles = new ArrayList<>();
        DatosResponsable responsable = responsables.buscarPorCi(ci);
        Empresa deResponsable = responsable == null || responsable.nroEmpresa() == null ? null
                : empresas.buscarPorId(responsable.nroEmpresa());
        if (deResponsable != null) {
            roles.add(new RolPersona(RolPersona.Rol.RESPONSABLE, deResponsable.getNroEmpresa(),
                    deResponsable.getNombrePublico(), null));
        }
        for (Chofer c : choferes.buscarPorCi(ci)) {
            Empresa e = c.getEmpresa();
            roles.add(new RolPersona(RolPersona.Rol.CHOFER, e.getNroEmpresa(), e.getNombrePublico(),
                    c.getEstado() == null ? null : c.getEstado().name()));
        }
        return roles;
    }
}

package tse.lab.frontofficeciudadanos.service;

import java.util.ArrayList;
import java.util.List;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.ChoferDAO;
import tse.lab.frontofficeciudadanos.data.ResponsableDAO;
import tse.lab.frontofficeciudadanos.model.Chofer;
import tse.lab.frontofficeciudadanos.model.Empresa;
import tse.lab.frontofficeciudadanos.model.Responsable;

// Roles de una persona en carga.uy: responsable de una empresa y/o chofer de una o varias.
@Stateless
public class RolesService {

    @EJB
    private ResponsableDAO responsables;
    @EJB
    private ChoferDAO choferes;

    public List<RolPersona> rolesDe(String ci) {
        List<RolPersona> roles = new ArrayList<>();
        Responsable responsable = responsables.buscarPorCi(ci);
        if (responsable != null && responsable.getEmpresa() != null) {
            Empresa e = responsable.getEmpresa();
            roles.add(new RolPersona(RolPersona.Rol.RESPONSABLE, e.getNroEmpresa(), e.getNombrePublico(), null));
        }
        for (Chofer c : choferes.buscarPorCi(ci)) {
            Empresa e = c.getEmpresa();
            roles.add(new RolPersona(RolPersona.Rol.CHOFER, e.getNroEmpresa(), e.getNombrePublico(),
                    c.getEstado() == null ? null : c.getEstado().name()));
        }
        return roles;
    }
}

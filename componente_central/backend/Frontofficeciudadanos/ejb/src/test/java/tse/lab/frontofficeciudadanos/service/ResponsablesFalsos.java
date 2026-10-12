package tse.lab.frontofficeciudadanos.service;

import java.util.HashMap;
import java.util.Map;

import tse.lab.frontofficefuncionarios.api.gestiondeempresas.DatosResponsable;
import tse.lab.frontofficefuncionarios.api.gestiondeempresas.ResponsablesRemote;

// ResponsablesRemote en memoria para los tests (en WildFly es el EJB remoto de Frontofficefuncionarios).
public class ResponsablesFalsos implements ResponsablesRemote {

    private final Map<String, DatosResponsable> responsables = new HashMap<>();

    public ResponsablesFalsos con(String ci, Long nroEmpresa) {
        responsables.put(ci, new DatosResponsable(ci, null, nroEmpresa));
        return this;
    }

    @Override
    public DatosResponsable buscarPorCi(String ci) {
        return ci == null ? null : responsables.get(ci);
    }

    @Override
    public void completarEmail(String ci, String email) {
        DatosResponsable r = buscarPorCi(ci);
        if (r != null && (r.email() == null || r.email().isBlank()) && email != null) {
            responsables.put(ci, new DatosResponsable(ci, email, r.nroEmpresa()));
        }
    }
}

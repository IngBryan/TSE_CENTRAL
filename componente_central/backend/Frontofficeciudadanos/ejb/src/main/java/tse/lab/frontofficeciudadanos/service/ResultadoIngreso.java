package tse.lab.frontofficeciudadanos.service;

import java.util.List;

import tse.lab.frontofficeciudadanos.model.PerfilCiudadano;
import tse.lab.frontofficeciudadanos.pdi.ResultadoValidacion;

// sesionPermitida: VALIDADO y NO_DISPONIBLE (FA8, funcionalidades limitadas) inician sesion;
// NO_EXISTE (FA6) y MENOR_DE_EDAD (FA7) no. roles va vacio si no se inicia sesion.
public record ResultadoIngreso(PerfilCiudadano perfil, ResultadoValidacion resultado, boolean nuevo,
        boolean sesionPermitida, List<RolPersona> roles) {
}

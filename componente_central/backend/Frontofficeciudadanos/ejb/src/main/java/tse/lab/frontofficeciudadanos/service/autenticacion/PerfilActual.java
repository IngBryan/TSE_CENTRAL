package tse.lab.frontofficeciudadanos.service.autenticacion;

import java.util.List;

import tse.lab.model.Ciudadano;

// Perfil y roles tal como estan ahora en la base, para que el front muestre el estado vigente.
public record PerfilActual(Ciudadano perfil, List<RolPersona> roles) {
}

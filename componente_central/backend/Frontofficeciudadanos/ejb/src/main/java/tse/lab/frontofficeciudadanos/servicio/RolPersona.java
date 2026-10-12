package tse.lab.frontofficeciudadanos.servicio;

// Rol de una persona en una empresa. estado solo aplica a CHOFER (HABILITADO o PENDIENTE_VALIDACION).
public record RolPersona(Rol rol, Long nroEmpresa, String empresa, String estado) {

    public enum Rol {
        RESPONSABLE,
        CHOFER
    }
}

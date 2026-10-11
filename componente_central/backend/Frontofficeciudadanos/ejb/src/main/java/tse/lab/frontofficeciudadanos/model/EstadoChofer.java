package tse.lab.frontofficeciudadanos.model;

// Estado de la asociacion de una persona como chofer de una empresa (CU-02). Solo los HABILITADO
// se pueden asignar a viajes; PENDIENTE_VALIDACION espera a que CU-04 valide a la persona.
public enum EstadoChofer {
    HABILITADO,
    PENDIENTE_VALIDACION
}

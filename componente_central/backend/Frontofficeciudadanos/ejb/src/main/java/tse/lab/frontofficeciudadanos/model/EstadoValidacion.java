package tse.lab.frontofficeciudadanos.model;

// Estado de la identidad de un ciudadano frente a la PDI (CU-03).
public enum EstadoValidacion {
    PENDIENTE_VALIDACION,
    VALIDADO,
    RECHAZADO, // la cedula no existe en DNIC
    NO_HABILITADO // menor de edad
}

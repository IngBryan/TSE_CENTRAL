package tse.lab.model;

// Estado de la asociacion de una persona como chofer de una empresa (CU-02). Solo los HABILITADO
// se pueden asignar a viajes; PENDIENTE_VALIDACION espera a que CU-04 valide a la persona.
// DADO_DE_BAJA: el responsable lo quito de la empresa. Es una baja logica (sus guias anteriores lo siguen
// referenciando); no aparece en el listado y, si se lo vuelve a agregar, se reactiva la misma asociacion.
public enum EstadoChofer {
    HABILITADO,
    PENDIENTE_VALIDACION,
    DADO_DE_BAJA
}

package tse.lab.model;

// Que hizo el central con un evento de la app movil (politica de resolucion de conflictos):
// APLICADO: valido, se registro y (si corresponde) cambio el estado de la guia.
// SIN_EFECTO: el efecto ya estaba aplicado (p. ej. INICIO con la guia ya EN_CURSO desde otro
//   telefono); se registra como hecho pero la guia no cambia. Para el movil es un exito.
// CONFLICTO: el evento no se puede aplicar al estado actual (p. ej. una carga despues de FIN, o la
//   guia fue reasignada a otro chofer). Se guarda igual, para que el responsable lo vea, y la guia
//   no cambia. El movil lo muestra como rechazado y no lo reintenta.
public enum ResultadoEventoViaje {
    APLICADO,
    SIN_EFECTO,
    CONFLICTO
}

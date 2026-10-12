package tse.lab.frontofficeciudadanos.model;

// Estado de una guia de viaje. CU-07: una vez ASIGNADA no se puede volver a asignar. Despues la
// mueve el chofer desde la app movil: INICIO la pasa a EN_CURSO y FIN a FINALIZADA (EventoViaje).
public enum EstadoGuia {
    SIN_ASIGNAR,
    ASIGNADA,
    EN_CURSO,
    FINALIZADA,
    // Preparado para la politica de conflictos (caso 2): todavia no hay forma de cancelar una guia
    // desde el frontoffice. Un evento del movil sobre una guia cancelada queda como CONFLICTO.
    CANCELADA
}

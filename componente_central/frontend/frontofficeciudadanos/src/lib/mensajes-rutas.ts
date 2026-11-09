/** Mensajes de rutas destinados al usuario; no exponer detalles técnicos del servidor. */
export type ErrorRuta = { error?: string; detalle?: string } | null;

export function mensajeErrorRutas(status: number, data: ErrorRuta): string {
  switch (data?.error) {
    case "localidades_invalidas": return "Elegí un origen y un destino válidos y diferentes.";
    case "origen_destino_desactualizado": return "El recorrido elegido no coincide con el de la guía. Revisá las localidades.";
    case "rutas_actualizadas": return "La ruta cambió. Volvé a calcularla antes de confirmar.";
    case "previsualizacion_requerida": return "Primero calculá la ruta para poder confirmarla.";
    case "viaje_no_modificable": return "Esta guía ya no permite modificar su ruta.";
    case "sin_ruta_vial": return "No encontramos un recorrido vial entre esas localidades.";
    case "geometria_no_encontrada": return "La ruta ya no está disponible. Volvé a calcular la ruta.";
    case "osrm_no_disponible":
    case "osrm_interrumpido":
    case "osrm_respuesta_invalida":
    case "osrm_configuracion": return "No pudimos calcular la ruta ahora. Intentá nuevamente más tarde.";
  }
  if (status === 403) return "No tenés permiso para gestionar rutas de esta guía.";
  if (status === 404) return "No encontramos la guía o la localidad solicitada.";
  if (status === 409) return "La guía o la ruta cambiaron. Actualizá la página y volvé a intentar.";
  if (status === 400 || status === 422) return "Revisá las localidades y volvé a calcular la ruta.";
  return "No pudimos completar la operación. Intentá nuevamente más tarde.";
}

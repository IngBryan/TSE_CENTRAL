/** Mensajes de rutas destinados al usuario; no exponer detalles técnicos del servidor. */
export type ErrorRuta = { error?: string; detalle?: string } | null;

export function mensajeErrorRutas(status: number, data: ErrorRuta): string {
  switch (data?.error) {
    case "localidades_invalidas": return "Elegí un origen y un destino válidos y diferentes.";
    case "origen_destino_desactualizado": return "El recorrido elegido no coincide con el de la guía. Revisá las localidades.";
    case "rutas_actualizadas": return "Las rutas cambiaron. Volvé a buscarlas antes de confirmar.";
    case "previsualizacion_requerida": return "Primero buscá las rutas para poder confirmarlas.";
    case "viaje_no_modificable": return "Esta guía ya no permite modificar sus rutas.";
    case "sin_ruta_vial": return "No encontramos un recorrido vial entre esas localidades.";
    case "geometria_no_encontrada": return "La ruta ya no está disponible. Volvé a buscar alternativas.";
    case "osrm_no_disponible":
    case "osrm_interrumpido":
    case "osrm_respuesta_invalida":
    case "osrm_configuracion": return "No pudimos calcular las rutas ahora. Intentá nuevamente más tarde.";
  }
  if (status === 403) return "No tenés permiso para gestionar rutas de esta guía.";
  if (status === 404) return "No encontramos la guía o la localidad solicitada.";
  if (status === 409) return "La guía o las rutas cambiaron. Actualizá la página y volvé a intentar.";
  if (status === 400 || status === 422) return "Revisá las localidades y volvé a buscar rutas.";
  return "No pudimos completar la operación. Intentá nuevamente más tarde.";
}

/** La primera confirmacion geométrica del GPS es el hito operativo inicial.
 * No se debe usar fechaDeteccion: esa es la hora administrativa de apertura del caso,
 * que puede ser posterior si Tracking reprocesa posiciones historicas (por ejemplo tras pausar una VM).
 * No muta ni reordena las evidencias persistidas.
 */
export function primeraConfirmacionGps(pruebas: readonly { fechaHora: string }[]): string | null {
  const fechas = pruebas.map(p => Date.parse(p.fechaHora)).filter(Number.isFinite);
  return fechas.length ? new Date(Math.min(...fechas)).toISOString() : null;
}

/** Fecha operativa estrictamente posterior a una confirmacion GPS (no a la apertura administrativa). */
export function desdeSeguimientoPosterior(
  inicioViaje: string | null | undefined, referenciaGps: string | null | undefined,
): string | null {
  const inicio = Date.parse(inicioViaje ?? "");
  const umbral = Date.parse(referenciaGps ?? "");
  if (!Number.isFinite(inicio) || !Number.isFinite(umbral)) return null;
  return new Date(Math.max(inicio, umbral + 1)).toISOString();
}

/** Las rutas se eligen por las huellas persistidas en CADA confirmación, no por la Guia actual.
 * Un hash ausente (o una geometria alterada que el servidor rechazo) no se inventa.
 */
export function referenciasDePrueba<T extends { sha256: string }>(
  prueba: { hashesRutas: string[] } | null | undefined, disponibles: readonly T[],
): { rutas: T[]; faltantes: number } {
  if (!prueba) return { rutas: [], faltantes: 0 };
  const porHuella = new Map<string, T>(disponibles.map(r => [r.sha256, r]));
  const huellas = [...new Set(prueba.hashesRutas)];
  return {
    rutas: huellas.flatMap(h => porHuella.has(h) ? [porHuella.get(h)!] : []),
    faltantes: huellas.filter(h => !porHuella.has(h)).length,
  };
}

/** Los puntos anteriores o coetáneos a la confirmación inicial NO son seguimiento posterior. */
export function soloPosteriores<T extends { fechaHora: string }>(
  posiciones: readonly T[], referenciaGps: string | null | undefined,
): T[] {
  const umbral = Date.parse(referenciaGps ?? "");
  return Number.isFinite(umbral) ? posiciones.filter(p => Date.parse(p.fechaHora) > umbral) : [];
}

import type { EstadoViaje } from "./viajes";

/** Las guías asignadas ya pueden recibir GPS; las finalizadas solo muestran su historia. */
export function habilitaPollingGps(estado: EstadoViaje, desdeMs: number): boolean {
  return (estado === "ASIGNADA" || estado === "EN_CURSO") && Number.isFinite(desdeMs);
}

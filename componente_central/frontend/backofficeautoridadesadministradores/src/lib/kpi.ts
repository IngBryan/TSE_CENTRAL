import type { Dashboard } from "./reportes";

// One column of a KPI chart; comparativo is the same group in the compared period
export type Grupo = { etiqueta: string; valor: number; comparativo?: number };

const MES = new Intl.DateTimeFormat("es-UY", { month: "short", timeZone: "UTC" });

// "2026-03" -> "mar"; any other period label stays as it is
export function etiquetaPeriodo(periodo: string): string {
  return /^\d{4}-\d{2}$/.test(periodo) ? MES.format(new Date(`${periodo}-01T00:00:00Z`)).replace(".", "") : periodo;
}

// Months are matched without their year, so March is compared with the March before
function clavePeriodo(periodo: string): string {
  return /^\d{4}-\d{2}$/.test(periodo) ? periodo.slice(5) : periodo;
}

/** Lines up the groups of both periods by key, in the order of the current one. */
export function agrupar<T>(
  actual: T[],
  anterior: T[] | undefined,
  clave: (item: T) => string,
  valor: (item: T) => number,
  etiqueta: (item: T) => string = clave
): Grupo[] {
  const previos = new Map((anterior ?? []).map((item) => [clave(item), valor(item)]));
  return actual.map((item) => ({
    etiqueta: etiqueta(item),
    valor: valor(item),
    comparativo: anterior ? (previos.get(clave(item)) ?? 0) : undefined,
  }));
}

export const viajesPorPeriodo = (actual: Dashboard, anterior?: Dashboard | null) =>
  agrupar(actual.viajesPorPeriodo, anterior?.viajesPorPeriodo, (v) => clavePeriodo(v.periodo), (v) => v.cantidad,
    (v) => etiquetaPeriodo(v.periodo));

export const cargaPorRubro = (actual: Dashboard, anterior?: Dashboard | null) =>
  agrupar(actual.cargaPorRubro, anterior?.cargaPorRubro, (c) => c.rubro, (c) => c.toneladas);

export const infraccionesPorTipo = (actual: Dashboard, anterior?: Dashboard | null) =>
  agrupar(actual.infraccionesPorTipo, anterior?.infraccionesPorTipo, (i) => i.tipo, (i) => i.detectadas);

export const totalViajes = (d: Dashboard) => d.viajesPorPeriodo.reduce((total, v) => total + v.cantidad, 0);
export const totalToneladas = (d: Dashboard) => d.cargaPorRubro.reduce((total, c) => total + c.toneladas, 0);
export const totalDetectadas = (d: Dashboard) => d.infraccionesPorTipo.reduce((total, i) => total + i.detectadas, 0);

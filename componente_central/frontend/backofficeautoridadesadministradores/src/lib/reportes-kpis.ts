import type { FormatoKpi } from "@/components/Kpi";

import { COMPARATIVOS, type Comparativo } from "./reportes";
import type { FacetaDisponible, ValorFaceta } from "./reportes-backend";

// suma: adds up over the period. tasa: closed over detected cases. stock: how many there are at the end of it
export type Agregado = "suma" | "tasa" | "stock";

export const KPIS = {
  viajes: { formato: "numero", agregado: "suma", mejorSiBaja: false },
  toneladas: { formato: "toneladas", agregado: "suma", mejorSiBaja: false },
  infracciones: { formato: "numero", agregado: "suma", mejorSiBaja: true },
  casos_cerrados: { formato: "numero", agregado: "suma", mejorSiBaja: false },
  tasa: { formato: "porcentaje", agregado: "tasa", mejorSiBaja: false },
  empresas: { formato: "numero", agregado: "stock", mejorSiBaja: false },
  vehiculos: { formato: "numero", agregado: "stock", mejorSiBaja: false },
} as const satisfies Record<string, { formato: FormatoKpi; agregado: Agregado; mejorSiBaja: boolean }>;

export type IdKpi = keyof typeof KPIS;

// The names come from the backend (GET reports/kpis); the id stands in while it does not say
export type NombresKpi = Partial<Record<IdKpi, string>>;

export function nombreKpi(nombres: NombresKpi, id: IdKpi): string {
  return nombres[id] ?? id;
}

// Each one becomes a column under its KPI
// By the id the backend takes (POST reports/load)
export const MEDIDAS = {
  current: "Actual",
  previous: "Anterior",
  growth: "Crecimiento",
  difference: "Diferencia",
} as const;

export type Medida = keyof typeof MEDIDAS;

// The groupings and filters are the facets of the backend (GET reports/facets), by id: "month", "department"
export const AGRUPACION_POR_DEFECTO = "month";

// The id stands in for a facet the backend did not name (it did not answer)
export function nombreFaceta(facetas: readonly FacetaDisponible[] | null, id: string): string {
  return facetas?.find((faceta) => faceta.id === id)?.name ?? id;
}

// The grouping when the KPIs take it; if not, month or the first one they take. Null facetas: the backend could
// not say, so it stays
export function agrupacionPermitida(agrupacion: string, facetas: readonly FacetaDisponible[] | null): string {
  if (!facetas || facetas.some((faceta) => faceta.id === agrupacion)) return agrupacion;
  if (facetas.some((faceta) => faceta.id === AGRUPACION_POR_DEFECTO)) return AGRUPACION_POR_DEFECTO;
  return facetas[0]?.id ?? agrupacion;
}

// The values of each catalog facet, by id, as its filter lists them; error when they could not be loaded
export type CatalogosFaceta = Partial<Record<string, { valores: ValorFaceta[]; error?: string }>>;

// The facets a report filters by: time is filtered by the period
export function facetasDeFiltro(facetas: readonly FacetaDisponible[]): FacetaDisponible[] {
  return facetas.filter((faceta) => faceta.kind === "catalog");
}

// What the KPIs of the backend, by id, look like here; a KPI without an entry is not offered
export function esIdKpi(id: string): id is IdKpi {
  return id in KPIS;
}

export type KpiReporte = { id: IdKpi; medidas: Medida[] };

export type Reporte = { kpis: KpiReporte[]; agrupacion: string };

// The whole report shares one comparison, chosen with the period; it starts here when a KPI first needs it
export const COMPARATIVO_POR_DEFECTO: Comparativo = "anio-calendario";

export const REPORTE_POR_DEFECTO: Reporte = {
  kpis: [{ id: "viajes", medidas: ["current"] }],
  agrupacion: AGRUPACION_POR_DEFECTO,
};

// The whole history has nothing to be compared with: only the current value
export function sinComparar(kpis: KpiReporte[]): KpiReporte[] {
  return kpis.map((kpi) => ({ ...kpi, medidas: ["current"] }));
}

export function usaComparativo(kpi: KpiReporte): boolean {
  return kpi.medidas.some((m) => m !== "current");
}

// The comparison only exists while some KPI shows something other than the current value
export function reporteCompara(reporte: Reporte): boolean {
  return reporte.kpis.some(usaComparativo);
}

const ORDEN_MEDIDAS = Object.keys(MEDIDAS) as Medida[];

// In the URL: "kpis=viajes:current|growth,toneladas:current"
function leerKpi(texto: string): KpiReporte | null {
  const [id, medidasTexto = "current"] = texto.split(":");
  if (!(id in KPIS)) return null;

  const medidas = ORDEN_MEDIDAS.filter((m) => medidasTexto.split("|").includes(m));
  return { id: id as IdKpi, medidas: medidas.length ? medidas : ["current"] };
}

export function leerReporte(params: Record<string, string | string[] | undefined>): Reporte {
  const texto = (clave: string) => (typeof params[clave] === "string" ? (params[clave] as string) : "");

  // Each KPI once, in the order it was added
  const vistos = new Set<string>();
  const kpis = texto("kpis")
    .split(",")
    .map(leerKpi)
    .filter((kpi): kpi is KpiReporte => kpi !== null && !vistos.has(kpi.id) && Boolean(vistos.add(kpi.id)));

  const agrupacion = texto("agrupacion");

  return {
    kpis: kpis.length ? kpis : REPORTE_POR_DEFECTO.kpis,
    agrupacion: /^[A-Za-z]{1,40}$/.test(agrupacion) ? agrupacion : REPORTE_POR_DEFECTO.agrupacion,
  };
}

export function consultaReporte(reporte: Reporte): string {
  const kpis = reporte.kpis.map((kpi) => `${kpi.id}:${kpi.medidas.join("|")}`);
  return new URLSearchParams({ kpis: kpis.join(","), agrupacion: reporte.agrupacion }).toString();
}

function enLista(nombres: string[]): string {
  return nombres.length <= 1 ? nombres.join("") : `${nombres.slice(0, -1).join(", ")} y ${nombres.at(-1)}`;
}

function minuscula(texto: string): string {
  return texto.charAt(0).toLowerCase() + texto.slice(1);
}

// "el año anterior (calendario)" or "el período 01/10/2025 – 31/10/2025"
export function textoComparativo(comparar: Comparativo, rangoComparar: string | null): string {
  return comparar === "personalizado" && rangoComparar
    ? `el período ${rangoComparar}`
    : `el ${minuscula(COMPARATIVOS[comparar])}`;
}

/**
 * What the report shows, in a sentence: "Viajes realizados y empresas habilitadas por mes, este año
 * (01/01/2026 – 07/11/2026), comparados con el año anterior (calendario)." `contra` is the comparison as
 * textoComparativo writes it, or null when the report compares nothing.
 */
export function describirReporte(
  reporte: Reporte,
  nombres: NombresKpi,
  facetas: readonly FacetaDisponible[] | null,
  periodo: string, rango: string | null, contra: string | null): string {
  const titulos = reporte.kpis.map((kpi, i) => (i === 0 ? nombreKpi(nombres, kpi.id) : minuscula(nombreKpi(nombres, kpi.id))));
  const comparados = reporte.kpis.filter(usaComparativo);

  let comparacion = "";
  if (contra && comparados.length === reporte.kpis.length) {
    comparacion = `, ${comparados.length > 1 ? "comparados" : "comparado"} con ${contra}`;
  } else if (contra && comparados.length > 0) {
    // Only some KPIs compare: name them in a second sentence
    const cuales = enLista(comparados.map((kpi) => minuscula(nombreKpi(nombres, kpi.id))));
    const verbo = comparados.length > 1 ? "se comparan" : "se compara";
    comparacion = `. ${cuales.charAt(0).toUpperCase()}${cuales.slice(1)} ${verbo} con ${contra}`;
  }

  return `${enLista(titulos)} por ${minuscula(nombreFaceta(facetas, reporte.agrupacion))}, ${minuscula(periodo)}${rango ? ` (${rango})` : ""}${comparacion}.`;
}

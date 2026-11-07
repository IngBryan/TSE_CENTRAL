import type { FormatoKpi } from "@/components/Kpi";

import { COMPARATIVOS, type Comparativo } from "./reportes";

// suma: adds up over the period. tasa: confirmed over detected. stock: how many there are at the end of it
export type Agregado = "suma" | "tasa" | "stock";

export const KPIS = {
  viajes: { nombre: "Viajes realizados", formato: "numero", agregado: "suma", mejorSiBaja: false },
  toneladas: { nombre: "Volumen de carga transportado", formato: "toneladas", agregado: "suma", mejorSiBaja: false },
  infracciones: { nombre: "Infracciones detectadas", formato: "numero", agregado: "suma", mejorSiBaja: true },
  confirmadas: { nombre: "Infracciones confirmadas", formato: "numero", agregado: "suma", mejorSiBaja: true },
  tasa: {
    nombre: "Tasa de infracciones confirmadas sobre detectadas",
    formato: "porcentaje",
    agregado: "tasa",
    mejorSiBaja: false,
  },
  empresas: { nombre: "Empresas habilitadas", formato: "numero", agregado: "stock", mejorSiBaja: false },
  vehiculos: { nombre: "Vehículos habilitados", formato: "numero", agregado: "stock", mejorSiBaja: false },
} as const satisfies Record<string, { nombre: string; formato: FormatoKpi; agregado: Agregado; mejorSiBaja: boolean }>;

export type IdKpi = keyof typeof KPIS;

// Each one becomes a column under its KPI
export const MEDIDAS = {
  actual: "Actual",
  anterior: "Anterior",
  crecimiento: "Crecimiento",
  diferencia: "Diferencia",
} as const;

export type Medida = keyof typeof MEDIDAS;

export const AGRUPACIONES = {
  dia: "Día",
  semana: "Semana",
  mes: "Mes",
  anio: "Año",
  departamento: "Departamento",
  tipoCarga: "Tipo de carga",
  rubro: "Rubro",
} as const;

export type Agrupacion = keyof typeof AGRUPACIONES;

export const AGRUPACIONES_TIEMPO: readonly Agrupacion[] = ["dia", "semana", "mes", "anio"];

export type KpiReporte = { id: IdKpi; medidas: Medida[] };

export type Reporte = { kpis: KpiReporte[]; agrupacion: Agrupacion };

// The whole report shares one comparison, chosen with the period; it starts here when a KPI first needs it
export const COMPARATIVO_POR_DEFECTO: Comparativo = "anio-calendario";

export const REPORTE_POR_DEFECTO: Reporte = {
  kpis: [{ id: "viajes", medidas: ["actual"] }],
  agrupacion: "mes",
};

export function usaComparativo(kpi: KpiReporte): boolean {
  return kpi.medidas.some((m) => m !== "actual");
}

// The comparison only exists while some KPI shows something other than the current value
export function reporteCompara(reporte: Reporte): boolean {
  return reporte.kpis.some(usaComparativo);
}

const ORDEN_MEDIDAS = Object.keys(MEDIDAS) as Medida[];

// In the URL: "kpis=viajes:actual|crecimiento,toneladas:actual"
function leerKpi(texto: string): KpiReporte | null {
  const [id, medidasTexto = "actual"] = texto.split(":");
  if (!(id in KPIS)) return null;

  const medidas = ORDEN_MEDIDAS.filter((m) => medidasTexto.split("|").includes(m));
  return { id: id as IdKpi, medidas: medidas.length ? medidas : ["actual"] };
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
    agrupacion: agrupacion in AGRUPACIONES ? (agrupacion as Agrupacion) : REPORTE_POR_DEFECTO.agrupacion,
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
export function describirReporte(reporte: Reporte, periodo: string, rango: string, contra: string | null): string {
  const nombres = reporte.kpis.map((kpi, i) => (i === 0 ? KPIS[kpi.id].nombre : minuscula(KPIS[kpi.id].nombre)));
  const comparados = reporte.kpis.filter(usaComparativo);

  let comparacion = "";
  if (contra && comparados.length === reporte.kpis.length) {
    comparacion = `, ${comparados.length > 1 ? "comparados" : "comparado"} con ${contra}`;
  } else if (contra && comparados.length > 0) {
    // Only some KPIs compare: name them in a second sentence
    const cuales = enLista(comparados.map((kpi) => minuscula(KPIS[kpi.id].nombre)));
    const verbo = comparados.length > 1 ? "se comparan" : "se compara";
    comparacion = `. ${cuales.charAt(0).toUpperCase()}${cuales.slice(1)} ${verbo} con ${contra}`;
  }

  return `${enLista(nombres)} por ${minuscula(AGRUPACIONES[reporte.agrupacion])}, ${minuscula(periodo)} (${rango})${comparacion}.`;
}

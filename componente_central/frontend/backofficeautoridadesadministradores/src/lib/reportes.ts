export const PERIODOS = {
  hoy: "Hoy",
  "ultimos-30-dias": "Últimos 30 días",
  "ultimos-90-dias": "Últimos 90 días",
  "este-mes": "Este mes",
  "este-anio": "Este año",
  historico: "Histórico completo",
  personalizado: "Personalizado",
} as const;

export const COMPARATIVOS = {
  "anio-calendario": "Año anterior (calendario)",
  "anio-52-semanas": "Año anterior (52 semanas)",
  personalizado: "Personalizado",
} as const;

export type Periodo = keyof typeof PERIODOS;
export type Comparativo = keyof typeof COMPARATIVOS;

// Dates are "YYYY-MM-DD", both ends included
export type Rango = { desde: string; hasta: string };

// What the URL carries: a preset stays relative ("last 30 days" means the last 30 days when the link is opened)
// and only "personalizado" brings its own dates
export type FiltrosDashboard = {
  periodo: Periodo;
  rango?: Rango;
  comparar?: Comparativo;
  rangoComparar?: Rango;
  // By facet id, as the backend names them: "cargoType", "department", "category"
  selecciones: Selecciones;
};

// Each value of a filter can be included, excluded or left alone. The filter keeps the included values (all of
// them when none is included) minus the excluded ones. A value is never in both lists; both empty is no filter
export type Seleccion = { incluir: string[]; excluir: string[] };

export type Selecciones = Partial<Record<string, Seleccion>>;

export function seleccionVacia(seleccion: Seleccion | undefined): boolean {
  return !seleccion || (seleccion.incluir.length === 0 && seleccion.excluir.length === 0);
}

// The filters as they travel in the URL: "incluir.cargoType=2,4&excluir.cargoType=11"
const INCLUIR = "incluir.";
const EXCLUIR = "excluir.";
const ID_FACETA = /^[A-Za-z]{1,40}$/;

function escribirSelecciones(query: URLSearchParams, selecciones: Selecciones) {
  for (const [faceta, seleccion] of Object.entries(selecciones)) {
    if (seleccionVacia(seleccion)) continue;
    if (seleccion!.incluir.length) query.set(INCLUIR + faceta, seleccion!.incluir.join(","));
    if (seleccion!.excluir.length) query.set(EXCLUIR + faceta, seleccion!.excluir.join(","));
  }
}

// Only the facets in `facetas`; the others are dropped (a company has no department)
export function seleccionesDe(selecciones: Selecciones, facetas: readonly string[]): Selecciones {
  return Object.fromEntries(Object.entries(selecciones).filter(([faceta]) => facetas.includes(faceta)));
}

// The name of a value by its id; the id when the list does not have it. Not in a client module: the pages
// use it on the server
export function nombreValor(valores: { id: string; name: string }[]): (id: string) => string {
  return (id) => valores.find((v) => v.id === id)?.name ?? id;
}

// "Todos", "Salto, Rivera", "Todos menos Salto", "Soja, Trigo (sin Arena)"; long lists become a count
export function textoSeleccion(seleccion: Seleccion | undefined, nombre: (valor: string) => string): string {
  if (seleccionVacia(seleccion)) return "Todos";
  const lista = (valores: string[], cuenta: string) =>
    valores.length <= 2 ? valores.map(nombre).join(", ") : `${valores.length} ${cuenta}`;

  const { incluir, excluir } = seleccion!;
  if (!incluir.length) return `Todos menos ${lista(excluir, "valores")}`;
  const incluidos = lista(incluir, "seleccionados");
  return excluir.length ? `${incluidos} (sin ${lista(excluir, "excluidos")})` : incluidos;
}

export const PERIODO_POR_DEFECTO: Periodo = "ultimos-30-dias";

const FECHA = /^\d{4}-\d{2}-\d{2}$/;

function esFecha(valor: string | undefined): valor is string {
  return valor !== undefined && FECHA.test(valor) && !Number.isNaN(Date.parse(`${valor}T00:00:00Z`));
}

function leerRango(desde: string | undefined, hasta: string | undefined): Rango | undefined {
  return esFecha(desde) && esFecha(hasta) && desde <= hasta ? { desde, hasta } : undefined;
}

// What a page uses when the URL does not say: Reportes looks at this month (each KPI picks its own comparison), the
// dashboard at this year against the year before
export type Predeterminados = { periodo: Periodo; comparar?: Comparativo };

export const PREDETERMINADOS_REPORTES: Predeterminados = { periodo: "este-mes" };
export const PREDETERMINADOS_DASHBOARD: Predeterminados = { periodo: "este-anio", comparar: "anio-calendario" };

// "comparar=ninguno" says it explicitly, so a page with a default comparison can drop it
export const SIN_COMPARAR = "ninguno";

// Invalid values are dropped instead of failing the page: the filter just does not apply
export function leerFiltros(
  params: Record<string, string | string[] | undefined>,
  predeterminados: Predeterminados = PREDETERMINADOS_REPORTES
): FiltrosDashboard {
  const valor = (clave: string) => {
    const v = params[clave];
    return typeof v === "string" && v.trim() ? v.trim() : undefined;
  };

  // Repeated values are dropped; a value in both lists stays excluded. The backend checks the ids
  const lista = (parametro: string) =>
    [...new Set((valor(parametro) ?? "").split(",").map((v) => v.trim()))].filter((v) => v.length > 0 && v.length <= 60);

  const selecciones: Selecciones = {};
  const facetas = new Set(
    Object.keys(params)
      .filter((clave) => clave.startsWith(INCLUIR) || clave.startsWith(EXCLUIR))
      .map((clave) => clave.slice(clave.indexOf(".") + 1))
      .filter((faceta) => ID_FACETA.test(faceta))
  );
  for (const faceta of facetas) {
    const excluir = lista(EXCLUIR + faceta);
    const incluir = lista(INCLUIR + faceta).filter((v) => !excluir.includes(v));
    if (incluir.length || excluir.length) selecciones[faceta] = { incluir, excluir };
  }

  const periodo = valor("periodo");
  const comparar = valor("comparar");

  const filtros: FiltrosDashboard = {
    periodo: periodo && periodo in PERIODOS ? (periodo as Periodo) : predeterminados.periodo,
    selecciones,
  };

  if (filtros.periodo === "personalizado") {
    filtros.rango = leerRango(valor("desde"), valor("hasta"));
    if (!filtros.rango) filtros.periodo = predeterminados.periodo;
  }

  if (comparar === SIN_COMPARAR) {
    return filtros;
  }

  if (comparar && comparar in COMPARATIVOS) {
    filtros.comparar = comparar as Comparativo;
    if (comparar === "personalizado") {
      filtros.rangoComparar = leerRango(valor("compararDesde"), valor("compararHasta"));
      if (!filtros.rangoComparar) filtros.comparar = predeterminados.comparar;
    }
  } else {
    filtros.comparar = predeterminados.comparar;
  }

  return filtros;
}

// Same shape the form submits, so the URL round-trips
export function consultaFiltros(filtros: FiltrosDashboard): string {
  const query = new URLSearchParams({ periodo: filtros.periodo });
  if (filtros.rango) {
    query.set("desde", filtros.rango.desde);
    query.set("hasta", filtros.rango.hasta);
  }
  query.set("comparar", filtros.comparar ?? SIN_COMPARAR);
  if (filtros.rangoComparar) {
    query.set("compararDesde", filtros.rangoComparar.desde);
    query.set("compararHasta", filtros.rangoComparar.hasta);
  }
  escribirSelecciones(query, filtros.selecciones);
  return query.toString();
}

// Date arithmetic on "YYYY-MM-DD" strings, in UTC so no timezone shifts the day
function aFecha(fecha: string): Date {
  return new Date(`${fecha}T00:00:00Z`);
}

function aTexto(fecha: Date): string {
  return fecha.toISOString().slice(0, 10);
}

function sumarDias(fecha: string, dias: number): string {
  const resultado = aFecha(fecha);
  resultado.setUTCDate(resultado.getUTCDate() + dias);
  return aTexto(resultado);
}

// Feb 29 goes to Feb 28 in a non-leap year
function restarAnio(fecha: string): string {
  const [anio, mes, dia] = fecha.split("-").map(Number);
  const ultimoDia = new Date(Date.UTC(anio - 1, mes, 0)).getUTCDate();
  return aTexto(new Date(Date.UTC(anio - 1, mes - 1, Math.min(dia, ultimoDia))));
}

// Today in Uruguay, whatever the server or browser timezone is
export function hoyEnUruguay(): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Montevideo" }).format(new Date());
}

// null for the whole history: the backend takes it from the first trip or case to today
export function rangoPeriodo(periodo: Periodo, rango: Rango | undefined, hoy: string): Rango | null {
  switch (periodo) {
    case "hoy":
      return { desde: hoy, hasta: hoy };
    case "historico":
      return null;
    case "ultimos-30-dias":
      return { desde: sumarDias(hoy, -29), hasta: hoy };
    case "ultimos-90-dias":
      return { desde: sumarDias(hoy, -89), hasta: hoy };
    case "este-mes":
      return { desde: `${hoy.slice(0, 7)}-01`, hasta: hoy };
    case "este-anio":
      return { desde: `${hoy.slice(0, 4)}-01-01`, hasta: hoy };
    case "personalizado":
      return rango ?? rangoPeriodo(PERIODO_POR_DEFECTO, undefined, hoy);
  }
}

// Calendar: same dates one year earlier. 52 weeks: 364 days earlier, so every day keeps its weekday
export function rangoComparacion(
  comparar: Comparativo | undefined,
  periodo: Rango,
  rangoComparar: Rango | undefined
): Rango | null {
  switch (comparar) {
    case "anio-calendario":
      return { desde: restarAnio(periodo.desde), hasta: restarAnio(periodo.hasta) };
    case "anio-52-semanas":
      return { desde: sumarDias(periodo.desde, -364), hasta: sumarDias(periodo.hasta, -364) };
    case "personalizado":
      return rangoComparar ?? null;
    default:
      return null;
  }
}

// "2026-10-09" -> "09/10/2026"
export function fechaCorta(fecha: string): string {
  return fecha.split("-").reverse().join("/");
}

export function textoRango(rango: Rango): string {
  return `${fechaCorta(rango.desde)} – ${fechaCorta(rango.hasta)}`;
}

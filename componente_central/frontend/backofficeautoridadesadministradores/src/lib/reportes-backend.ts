import { fetchBackend, idSesion, urlBackend, wildFlyAuthorization } from "./backoffice-sesion";

// Contracts of the reports API of the backoffice (/api/reports)
export type ValorFaceta = { id: string; name: string };
export type KpiDisponible = { id: string; name: string };
// time: only groups (the period is its filter). catalog: groups and filters
export type FacetaDisponible = { id: string; name: string; kind: "time" | "catalog" };

export type Resultado<T> = { ok: true; datos: T } | { ok: false; mensaje: string };

const SESION_VENCIDA = "La sesión venció. Vuelve a iniciar sesión.";

function esObjeto(valor: unknown): valor is Record<string, unknown> {
  return valor !== null && typeof valor === "object";
}

function conIdYNombre(valor: unknown): valor is { id: string; name: string } {
  return esObjeto(valor) && typeof valor.id === "string" && typeof valor.name === "string";
}

function esFaceta(valor: unknown): valor is FacetaDisponible {
  return conIdYNombre(valor) && ["time", "catalog"].includes((valor as Record<string, unknown>).kind as string);
}

// A call to the API with the session (a GET, or a POST of `cuerpo`); `leer` checks the JSON and returns what the
// caller wants of it, or null. A 400 brings what was wrong in "detalle"
async function consultar<T>(
  ruta: string,
  leer: (datos: unknown) => T | null,
  error: string,
  cuerpo?: unknown
): Promise<Resultado<T>> {
  const sesion = await idSesion();
  if (!sesion) return { ok: false, mensaje: SESION_VENCIDA };

  try {
    const respuesta = await fetchBackend(urlBackend(ruta), {
      method: cuerpo === undefined ? "GET" : "POST",
      headers: {
        Accept: "application/json",
        Authorization: wildFlyAuthorization(sesion),
        ...(cuerpo === undefined ? {} : { "Content-Type": "application/json" }),
      },
      body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
      cache: "no-store",
    });
    if (respuesta.status === 401) return { ok: false, mensaje: SESION_VENCIDA };
    if (respuesta.status === 400) {
      const detalle: unknown = await respuesta.json().catch(() => null);
      if (esObjeto(detalle) && typeof detalle.detalle === "string") {
        return { ok: false, mensaje: `${error} ${detalle.detalle}` };
      }
    }
    if (!respuesta.ok) return { ok: false, mensaje: error };

    const datos = leer(await respuesta.json());
    return datos === null ? { ok: false, mensaje: "La respuesta del backend no es válida." } : { ok: true, datos };
  } catch {
    return { ok: false, mensaje: "No se pudo establecer conexión con el backend." };
  }
}

// Every value of a catalog facet, with no filter: what a filter lists
export async function valoresFaceta(facetId: string): Promise<Resultado<ValorFaceta[]>> {
  return consultar(
    `reports/facets/${encodeURIComponent(facetId)}/values`,
    (datos) => (esObjeto(datos) && Array.isArray(datos.values) && datos.values.every(conIdYNombre) ? datos.values : null),
    "No se pudieron cargar los valores del filtro."
  );
}

// The KPIs a report can ask for, in the order the backend offers them
export async function listarKpis(): Promise<Resultado<KpiDisponible[]>> {
  return consultar(
    "reports/kpis",
    (datos) => (Array.isArray(datos) && datos.every(conIdYNombre) ? datos : null),
    "No se pudieron cargar los KPIs."
  );
}

// The facets a report of all these KPIs at once can be grouped and filtered by
export async function facetasDeKpis(kpis: string[]): Promise<Resultado<FacetaDisponible[]>> {
  const consulta = new URLSearchParams(kpis.map((kpi) => ["kpi", kpi])).toString();
  return consultar(
    `reports/facets?${consulta}`,
    (datos) => (esObjeto(datos) && Array.isArray(datos.facets) && datos.facets.every(esFaceta) ? datos.facets : null),
    "No se pudieron cargar las agrupaciones y filtros."
  );
}

// Body of POST reports/load: whole days, "2026-11-01T00:00:00" to "2026-11-07T23:59:59.999"
export type Rango = { from: string; to: string };

export type PedidoReporte = {
  kpis: { id: string; measures: string[] }[];
  period: Rango | null;
  comparison: Rango | null;
  groupBy: string;
  filters: Record<string, { include: string[]; exclude: string[] }>;
};

// By KPI id, its measures by id; a measure without value (the growth from zero) is null
export type ValoresReporte = Record<string, Record<string, number | null>>;

// group is null for the rows without a value of the facet
export type RespuestaReporte = {
  groupBy: string;
  rows: { group: ValorFaceta | null; values: ValoresReporte }[];
  total: ValoresReporte;
};

function sonValores(valor: unknown): valor is ValoresReporte {
  return (
    esObjeto(valor) &&
    Object.values(valor).every(
      (medidas) => esObjeto(medidas) && Object.values(medidas).every((v) => v === null || typeof v === "number")
    )
  );
}

function esFila(valor: unknown): valor is RespuestaReporte["rows"][number] {
  return esObjeto(valor) && (valor.group === null || conIdYNombre(valor.group)) && sonValores(valor.values);
}

export async function cargarReporte(pedido: PedidoReporte): Promise<Resultado<RespuestaReporte>> {
  return consultar(
    "reports/load",
    (datos) =>
      esObjeto(datos) &&
      typeof datos.groupBy === "string" &&
      Array.isArray(datos.rows) &&
      datos.rows.every(esFila) &&
      sonValores(datos.total)
        ? (datos as RespuestaReporte)
        : null,
    "No se pudo cargar el reporte.",
    pedido
  );
}

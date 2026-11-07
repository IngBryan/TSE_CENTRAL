import {
  AGRUPACIONES_TIEMPO,
  KPIS,
  usaComparativo,
  type Agrupacion,
  type IdKpi,
  type Reporte,
} from "./reportes-kpis";
import {
  DEPARTAMENTOS,
  rangoComparacion,
  valoresFiltrados,
  type Comparativo,
  type Dashboard,
  type FiltrosCarga,
  type Rango,
  type Seleccion,
  type TipoCargaOpcion,
} from "./reportes";

// Test data for the dashboard while there is no backend for it. Deterministic: the same range and filters
// always give the same numbers, so a period and its comparison differ in a believable way.

const RUBROS: [string, number][] = [
  ["Agropecuario", 0.24], ["Forestal", 0.2], ["Construcción", 0.12], ["Industrial", 0.09],
  ["Alimentos y bebidas", 0.08], ["Combustibles y químicos", 0.07], ["Comercio y distribución", 0.07],
  ["Ganadero", 0.06], ["Minería", 0.03], ["Contenedores y portuario", 0.04],
];

// Share of the trips with each kind of case, and how many of the detected ones end up confirmed
const INFRACCIONES: [string, number, number][] = [
  ["Exceso de carga", 0.015, 0.72],
  ["Viaje sin guía", 0.008, 0.61],
  ["Vehículo sin habilitación", 0.006, 0.84],
  ["Desvío de ruta", 0.004, 0.47],
];

const TIPOS_CARGA = [
  "Granos a granel", "Soja a granel", "Maíz a granel", "Trigo", "Arroz", "Rollizos", "Rollizos de eucalipto",
  "Rollizos de pino", "Madera aserrada", "Materiales de obra", "Arena", "Ganado en pie", "Contenedor",
  "Carga refrigerada", "Combustibles liquidos", "Carga general",
];

// Harvest and forestry peaks: autumn (March to May) and spring (October, November)
const ESTACIONALIDAD = [0.85, 0.9, 1.2, 1.3, 1.25, 1.0, 0.85, 0.85, 0.95, 1.15, 1.2, 0.9];

const DEPARTAMENTOS_PESO: Record<string, number> = {
  Montevideo: 0.28, Canelones: 0.11, Paysandú: 0.08, Rivera: 0.07, Tacuarembó: 0.07, Soriano: 0.06,
  "Río Negro": 0.06, Colonia: 0.05, Durazno: 0.04, Salto: 0.04,
};

// Number in [0.9, 1.1) from a text, so each month, rubro and type has its own stable noise
function ruido(clave: string): number {
  let hash = 2166136261;
  for (const caracter of clave) {
    hash = Math.imul(hash ^ caracter.charCodeAt(0), 16777619);
  }
  return 0.9 + ((hash >>> 0) % 1000) / 5000;
}

function crecimiento(anio: number): number {
  return 1.08 ** (anio - 2025);
}

// Share of the activity of each department and cargo type; every list adds up to 1
function partes(claves: readonly string[], peso: (clave: string) => number): Map<string, number> {
  const total = claves.reduce((suma, clave) => suma + peso(clave), 0);
  return new Map(claves.map((clave) => [clave, peso(clave) / total]));
}

const PARTE_DEPARTAMENTO = partes(DEPARTAMENTOS, (d) => DEPARTAMENTOS_PESO[d] ?? 0.025);
const PARTE_TIPO_CARGA = partes(TIPOS_CARGA.map((_, i) => String(i + 1)), (id) => ruido(`tipo-${id}`));

// Share of what the filter leaves: the included values (all when none is), minus the excluded ones
function proporcionSeleccion(seleccion: Seleccion | undefined, parte: Map<string, number>): number {
  return valoresFiltrados(seleccion, [...parte.keys()]).reduce((suma, valor) => suma + (parte.get(valor) ?? 0), 0);
}

function proporcionFiltros(filtros: FiltrosCarga): number {
  return proporcionSeleccion(filtros.departamentos, PARTE_DEPARTAMENTO) * proporcionSeleccion(filtros.tiposCarga, PARTE_TIPO_CARGA);
}

// Share of the companies a filter keeps. A company works in several departments and cargo types, so filtering
// keeps more companies than trips, but every inclusion or exclusion still moves the number
function parteEmpresas(proporcion: number): number {
  return 0.25 + 0.75 * proporcion;
}

// Days of each month inside the range: "2026-03" -> 31
function diasPorMes(rango: Rango): Map<string, number> {
  const dias = new Map<string, number>();
  const dia = new Date(`${rango.desde}T00:00:00Z`);
  const fin = new Date(`${rango.hasta}T00:00:00Z`);
  while (dia <= fin) {
    const mes = dia.toISOString().slice(0, 7);
    dias.set(mes, (dias.get(mes) ?? 0) + 1);
    dia.setUTCDate(dia.getUTCDate() + 1);
  }
  return dias;
}

export function dashboardMock(rango: Rango, filtros: FiltrosCarga = {}): Dashboard {
  const proporcion = proporcionFiltros(filtros);
  const anio = Number(rango.hasta.slice(0, 4));

  const viajesPorPeriodo = [...diasPorMes(rango)].map(([periodo, dias]) => {
    const mes = Number(periodo.slice(5)) - 1;
    const viajes = 46 * dias * ESTACIONALIDAD[mes] * crecimiento(Number(periodo.slice(0, 4))) * ruido(periodo);
    return { periodo, cantidad: Math.round(viajes * proporcion) };
  });

  const viajes = viajesPorPeriodo.reduce((total, v) => total + v.cantidad, 0);

  const cargaPorRubro = RUBROS.map(([rubro, peso]) => ({
    rubro,
    toneladas: Math.round(viajes * 24.5 * peso * ruido(`${rubro}-${anio}`)),
  })).sort((a, b) => b.toneladas - a.toneladas);

  // Infractions go down about 10% a year as the controls settle in
  const mejora = 0.9 ** (anio - 2025);
  const infraccionesPorTipo = INFRACCIONES.map(([tipo, tasa, confirmacion]) => {
    // The share the filters leave is part of the seed, so the confirmed rate moves with every filter too
    const detectadas = Math.round(viajes * tasa * mejora * ruido(`${tipo}-${anio}`));
    return { tipo, detectadas, confirmadas: Math.round(detectadas * confirmacion * ruido(`${tipo}-c-${anio}-${proporcion.toFixed(4)}`)) };
  });

  const empresas = Math.round((168 + (anio - 2024) * 14) * parteEmpresas(proporcion));

  return {
    empresasHabilitadas: Math.max(empresas, 1),
    vehiculosHabilitados: Math.round(Math.max(empresas, 1) * 5.8 * ruido(`vehiculos-${anio}`)),
    viajesPorPeriodo,
    cargaPorRubro,
    infraccionesPorTipo,
  };
}

export function tiposCargaMock(): TipoCargaOpcion[] {
  return TIPOS_CARGA.map((nombre, i) => ({ id: i + 1, nombre }));
}

// The period and, if there is one, the compared period
export function dashboardConComparacion(
  periodo: Rango,
  comparacion: Rango | null,
  filtros?: FiltrosCarga
): { actual: Dashboard; anterior: Dashboard | null } {
  return {
    actual: dashboardMock(periodo, filtros),
    anterior: comparacion ? dashboardMock(comparacion, filtros) : null,
  };
}

// ---- Reportes: one row per group, one value per KPI ----

type Sumas = { viajes: number; toneladas: number; infracciones: number; confirmadas: number };

// What happened on one day across the whole country, before any filter
function metricasDia(fecha: string): Sumas {
  const anio = Number(fecha.slice(0, 4));
  const viajes = 46 * ESTACIONALIDAD[Number(fecha.slice(5, 7)) - 1] * crecimiento(anio) * ruido(fecha);
  const infracciones = viajes * 0.033 * 0.9 ** (anio - 2025) * ruido(`i-${fecha}`);
  return {
    viajes,
    toneladas: viajes * 24.5 * ruido(`t-${fecha}`),
    infracciones,
    confirmadas: infracciones * 0.66 * ruido(`c-${fecha}`),
  };
}

function dias(rango: Rango): string[] {
  const lista: string[] = [];
  const dia = new Date(`${rango.desde}T00:00:00Z`);
  const fin = new Date(`${rango.hasta}T00:00:00Z`);
  while (dia <= fin) {
    lista.push(dia.toISOString().slice(0, 10));
    dia.setUTCDate(dia.getUTCDate() + 1);
  }
  return lista;
}

const SUMAS = new Map<string, Sumas>();

function sumas(rango: Rango): Sumas {
  const clave = `${rango.desde}/${rango.hasta}`;
  const guardadas = SUMAS.get(clave);
  if (guardadas) return guardadas;

  const total: Sumas = { viajes: 0, toneladas: 0, infracciones: 0, confirmadas: 0 };
  for (const fecha of dias(rango)) {
    const dia = metricasDia(fecha);
    total.viajes += dia.viajes;
    total.toneladas += dia.toneladas;
    total.infracciones += dia.infracciones;
    total.confirmadas += dia.confirmadas;
  }
  SUMAS.set(clave, total);
  return total;
}

function correrDias(fecha: string, cantidad: number): string {
  const dia = new Date(`${fecha}T00:00:00Z`);
  dia.setUTCDate(dia.getUTCDate() + cantidad);
  return dia.toISOString().slice(0, 10);
}

function diasEntre(desde: string, hasta: string): number {
  return Math.round((Date.parse(`${hasta}T00:00:00Z`) - Date.parse(`${desde}T00:00:00Z`)) / 86_400_000);
}

// Companies enabled at a date: about 14 more every year
function empresasAl(fecha: string): number {
  const anio = Number(fecha.slice(0, 4));
  const mes = Number(fecha.slice(5, 7));
  return 168 + (anio - 2024) * 14 + ((mes - 1) * 14) / 12;
}

function valorKpi(id: IdKpi, rango: Rango, proporcion: number, clave: string): number {
  const kpi = KPIS[id];

  if (kpi.agregado === "stock") {
    // A department or cargo type keeps part of the companies (see parteEmpresas)
    const empresas = empresasAl(rango.hasta) * parteEmpresas(proporcion);
    return Math.round(id === "vehiculos" ? empresas * 5.8 * ruido(`v-${clave}`) : empresas);
  }

  const total = sumas(rango);
  if (kpi.agregado === "tasa") {
    return total.infracciones === 0
      ? 0
      : Math.min(1, (total.confirmadas / total.infracciones) * ruido(`tasa-${clave}-${proporcion.toFixed(4)}`));
  }

  return Math.round(total[id as keyof Sumas] * proporcion);
}

type GrupoReporte = { clave: string; etiqueta: string; rango: Rango; proporcion: number };

const MES_ANIO = new Intl.DateTimeFormat("es-UY", { month: "short", year: "numeric", timeZone: "UTC" });

function fechaDiaMes(fecha: string): string {
  return `${fecha.slice(8, 10)}/${fecha.slice(5, 7)}`;
}

// Time buckets cut to the period: a month that started before it only counts the days inside
function gruposTiempo(agrupacion: Agrupacion, periodo: Rango, proporcion: number): GrupoReporte[] {
  const grupos = new Map<string, GrupoReporte>();

  for (const fecha of dias(periodo)) {
    let clave: string;
    let etiqueta: string;

    if (agrupacion === "dia") {
      clave = fecha;
      etiqueta = fechaDiaMes(fecha);
    } else if (agrupacion === "semana") {
      const diaSemana = (new Date(`${fecha}T00:00:00Z`).getUTCDay() + 6) % 7;
      clave = correrDias(fecha, -diaSemana);
      etiqueta = `Semana del ${fechaDiaMes(clave)}`;
    } else if (agrupacion === "mes") {
      clave = fecha.slice(0, 7);
      etiqueta = MES_ANIO.format(new Date(`${clave}-01T00:00:00Z`)).replace(".", "");
    } else {
      clave = fecha.slice(0, 4);
      etiqueta = clave;
    }

    const grupo = grupos.get(clave);
    if (grupo) {
      grupo.rango.hasta = fecha;
    } else {
      grupos.set(clave, { clave, etiqueta, rango: { desde: fecha, hasta: fecha }, proporcion });
    }
  }

  return [...grupos.values()];
}

function gruposNegocio(agrupacion: Agrupacion, periodo: Rango, filtros: FiltrosCarga): GrupoReporte[] {
  if (agrupacion === "departamento") {
    const otros = proporcionSeleccion(filtros.tiposCarga, PARTE_TIPO_CARGA);
    return valoresFiltrados(filtros.departamentos, DEPARTAMENTOS).map((d) => ({
      clave: d,
      etiqueta: d,
      rango: periodo,
      proporcion: (PARTE_DEPARTAMENTO.get(d) ?? 0) * otros,
    }));
  }

  if (agrupacion === "tipoCarga") {
    const otros = proporcionSeleccion(filtros.departamentos, PARTE_DEPARTAMENTO);
    const ids = TIPOS_CARGA.map((_, i) => String(i + 1));
    return valoresFiltrados(filtros.tiposCarga, ids).map((id) => ({
      clave: id,
      etiqueta: TIPOS_CARGA[Number(id) - 1],
      rango: periodo,
      proporcion: (PARTE_TIPO_CARGA.get(id) ?? 0) * otros,
    }));
  }

  const total = RUBROS.reduce((suma, [, peso]) => suma + peso, 0);
  const otros = proporcionFiltros(filtros);
  return RUBROS.map(([rubro, peso]) => ({
    clave: rubro,
    etiqueta: rubro,
    rango: periodo,
    proporcion: (peso / total) * otros,
  }));
}

// The comparison of the whole report; a custom one brings its range
export type ComparativoReporte = { comparar: Comparativo; rangoComparar?: Rango };

// The range a group is compared with. Time groups move with the comparison (November against last November);
// the other groups are the same group over the other range
function rangoComparado(
  comparativo: ComparativoReporte,
  grupo: Rango,
  periodo: Rango,
  esTiempo: boolean
): Rango | null {
  const { comparar, rangoComparar } = comparativo;
  if (comparar !== "personalizado") {
    return rangoComparacion(comparar, grupo, undefined);
  }
  if (!rangoComparar) return null;
  if (!esTiempo || (grupo.desde === periodo.desde && grupo.hasta === periodo.hasta)) return rangoComparar;

  // Custom range: each time group keeps its distance from the start of the period
  const corrimiento = diasEntre(periodo.desde, rangoComparar.desde);
  return { desde: correrDias(grupo.desde, corrimiento), hasta: correrDias(grupo.hasta, corrimiento) };
}

export type ValorKpi = { actual: number; anterior: number | null };
export type FilaReporte = { clave: string; etiqueta: string; valores: Partial<Record<IdKpi, ValorKpi>> };
export type TablaReporte = { filas: FilaReporte[]; total: FilaReporte };

// Only the KPIs showing something other than the current value get the compared value
export function tablaReporteMock(
  reporte: Reporte,
  periodo: Rango,
  filtros: FiltrosCarga = {},
  comparativo: ComparativoReporte | null = null
): TablaReporte {
  const esTiempo = AGRUPACIONES_TIEMPO.includes(reporte.agrupacion);
  const proporcionTotal = proporcionFiltros(filtros);

  const fila = (grupo: GrupoReporte): FilaReporte => ({
    clave: grupo.clave,
    etiqueta: grupo.etiqueta,
    valores: Object.fromEntries(
      reporte.kpis.map((kpi) => {
        const comparado =
          comparativo && usaComparativo(kpi) ? rangoComparado(comparativo, grupo.rango, periodo, esTiempo) : null;
        const valor: ValorKpi = {
          actual: valorKpi(kpi.id, grupo.rango, grupo.proporcion, grupo.clave),
          anterior: comparado ? valorKpi(kpi.id, comparado, grupo.proporcion, grupo.clave) : null,
        };
        return [kpi.id, valor];
      })
    ),
  });

  const grupos = esTiempo
    ? gruposTiempo(reporte.agrupacion, periodo, proporcionTotal)
    : gruposNegocio(reporte.agrupacion, periodo, filtros);
  const filas = grupos.map(fila);

  // Business groups are ranked by the first KPI, the biggest first
  if (!esTiempo) {
    const primero = reporte.kpis[0].id;
    filas.sort((a, b) => (b.valores[primero]?.actual ?? 0) - (a.valores[primero]?.actual ?? 0));
  }

  const total = fila({ clave: "total", etiqueta: "Total", rango: periodo, proporcion: proporcionTotal });
  return { filas, total };
}

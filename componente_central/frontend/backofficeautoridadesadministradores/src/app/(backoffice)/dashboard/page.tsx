import { BadgeCheck, Building2, Package, Route, ShieldAlert, Table2, Truck } from "lucide-react";

import Kpi from "@/components/Kpi";
import { requerirRol } from "@/lib/backoffice-sesion";
import { kpiDashboard, tablaDashboard } from "@/lib/kpi";
import {
  consultaFiltros,
  hoyEnUruguay,
  leerFiltros,
  PERIODOS,
  PREDETERMINADOS_DASHBOARD,
  rangoComparacion,
  rangoPeriodo,
  seleccionesDe,
  textoRango,
  textoSeleccion,
  type FiltrosDashboard,
} from "@/lib/reportes";
import { cargarReporte, facetasDeKpis, listarKpis, valoresFaceta } from "@/lib/reportes-backend";
import { consultaReporte, esIdKpi, KPIS, type IdKpi } from "@/lib/reportes-kpis";
import { pedidoReporte } from "@/lib/reportes-tabla";
import BarraDashboard from "./BarraDashboard";

// The facet of the backend the dashboard filters by
const RUBRO = "category";
// The grouping of each card; the number cards only use the total, so any grouping does
const AGRUPACION = {
  empresas: "year",
  vehiculos: "year",
  tasa: "year",
  viajes: "month",
  toneladas: "category",
  infracciones: "caseType",
} as const satisfies Partial<Record<IdKpi, string>>;

// The facet the table of indicators groups by
const EMPRESA = "company";
// Grouped by company each company only counts itself: the backend allows it, but it says nothing in this table
const FUERA_DE_LA_TABLA: string[] = ["empresas"];

// This year against the same days of last year, unless the clock or the filters say otherwise
export default async function DashboardPage({ searchParams }: PageProps<"/dashboard">) {
  await requerirRol("AUTORIDAD");
  // Rubro is the only filter every KPI of the dashboard can take: companies and vehicles have no department or
  // cargo type
  const leidos = leerFiltros(await searchParams, PREDETERMINADOS_DASHBOARD);
  // The dashboard compares, so it does not take the whole history
  const filtros: FiltrosDashboard = {
    ...leidos,
    periodo: leidos.periodo === "historico" ? PREDETERMINADOS_DASHBOARD.periodo : leidos.periodo,
    selecciones: seleccionesDe(leidos.selecciones, [RUBRO]),
  };

  const hoy = hoyEnUruguay();
  const periodo = rangoPeriodo(filtros.periodo, filtros.rango, hoy) ?? { desde: hoy, hasta: hoy };
  const comparacion = rangoComparacion(filtros.comparar, periodo, filtros.rangoComparar);
  const compara = comparacion !== null;

  // One call per KPI, each with its own grouping
  const cargar = (id: IdKpi, agrupacion: string) =>
    cargarReporte(
      pedidoReporte(
        { kpis: [{ id, medidas }], agrupacion },
        periodo,
        comparacion,
        filtros
      )
    ).then((respuesta) => kpiDashboard(respuesta, id, compara));

  const medidas = compara ? ["current" as const, "previous" as const] : ["current" as const];

  // Each card leads to its KPIs as a report, with the same grouping, period, comparison and filters; there the
  // change shows too
  const enReportes = (ids: IdKpi[], agrupacion: string) =>
    `/reportes?${consultaFiltros(filtros)}&${consultaReporte({
      kpis: ids.map((id) => ({ id, medidas: compara ? ["current", "previous", "growth"] : ["current"] })),
      agrupacion,
    })}`;

  // Every indicator the backend can group by company, side by side in one table: one call, since they share the
  // grouping
  async function porEmpresa() {
    const kpis = await listarKpis();
    if (!kpis.ok) return { ok: false as const, mensaje: kpis.mensaje, kpis: [] };
    const conocidos = kpis.datos.filter((kpi) => esIdKpi(kpi.id) && !FUERA_DE_LA_TABLA.includes(kpi.id));
    const facetas = await Promise.all(conocidos.map((kpi) => facetasDeKpis([kpi.id])));
    const compatibles = conocidos.filter((_, i) => {
      const respuesta = facetas[i];
      return respuesta.ok && respuesta.datos.some((faceta) => faceta.id === EMPRESA);
    });

    const columnas = compatibles.map((kpi) => ({
      id: kpi.id,
      titulo: kpi.name,
      formato: KPIS[kpi.id as IdKpi].formato,
      mejorSiBaja: KPIS[kpi.id as IdKpi].mejorSiBaja,
    }));
    const ids = compatibles.map((kpi) => kpi.id as IdKpi);
    if (ids.length === 0) {
      return { ok: true as const, tabla: { encabezado: "Empresa", columnas, filas: [] }, kpis: ids };
    }
    const reporte = { kpis: ids.map((id) => ({ id, medidas })), agrupacion: EMPRESA };
    const respuesta = await cargarReporte(pedidoReporte(reporte, periodo, comparacion, filtros));
    return { ...tablaDashboard(respuesta, "Empresa", columnas, compara), kpis: ids };
  }

  const [rubros, empresas, vehiculos, tasa, viajes, toneladas, infracciones, tabla] = await Promise.all([
    valoresFaceta(RUBRO),
    cargar("empresas", AGRUPACION.empresas),
    cargar("vehiculos", AGRUPACION.vehiculos),
    cargar("tasa", AGRUPACION.tasa),
    cargar("viajes", AGRUPACION.viajes),
    cargar("toneladas", AGRUPACION.toneladas),
    cargar("infracciones", AGRUPACION.infracciones),
    porEmpresa(),
  ]);
  const valores = rubros.ok ? rubros.datos : [];

  const nombreRubro = (id: string) => valores.find((r) => r.id === id)?.name ?? id;
  const rubro = filtros.selecciones[RUBRO];
  const filtrosActivos = [rubro && `Rubro: ${textoSeleccion(rubro, nombreRubro)}`].filter(Boolean);

  // What the card shows of each KPI, or why it could not be loaded
  const datos = (kpi: typeof empresas) =>
    kpi.ok
      ? { valor: kpi.valor, comparativo: kpi.comparativo, agrupacion: kpi.grupos }
      : { valor: null, error: kpi.mensaje };

  return (
    <div className="w-full min-w-0 space-y-6 px-6 py-8 lg:px-8">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">Dashboard</h1>
          <p className="mt-2 text-muted-foreground">
            {PERIODOS[filtros.periodo]}: {textoRango(periodo)}
            {comparacion && ` · comparado con ${textoRango(comparacion)}`}
          </p>
          {filtrosActivos.length > 0 && (
            <p className="mt-1 text-sm text-muted-foreground">Filtros: {filtrosActivos.join(" · ")}</p>
          )}
        </div>

        {/* Remounted on each search so the popovers start from the applied filters */}
        <BarraDashboard
          key={consultaFiltros(filtros)}
          filtros={filtros}
          rubros={valores}
          errorRubros={rubros.ok ? undefined : rubros.mensaje}
          hoy={hoy}
        />
      </header>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Kpi
          titulo="Empresas habilitadas"
          icono={Building2}
          href={enReportes(["empresas"], AGRUPACION.empresas)}
          {...datos(empresas)}
        />
        <Kpi
          titulo="Vehículos habilitados"
          icono={Truck}
          href={enReportes(["vehiculos"], AGRUPACION.vehiculos)}
          {...datos(vehiculos)}
        />
        <Kpi
          titulo="Tasa de casos cerrados sobre detectados"
          icono={BadgeCheck}
          formato="porcentaje"
          href={enReportes(["tasa"], AGRUPACION.tasa)}
          {...datos(tasa)}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Kpi
          tipo="columnas"
          titulo="Viajes realizados por mes"
          icono={Route}
          href={enReportes(["viajes"], AGRUPACION.viajes)}
          {...datos(viajes)}
        />
        <Kpi
          tipo="columnas"
          titulo="Volumen de carga transportado por rubro"
          icono={Package}
          formato="toneladas"
          href={enReportes(["toneladas"], AGRUPACION.toneladas)}
          {...datos(toneladas)}
        />
        <Kpi
          tipo="columnas"
          titulo="Infracciones detectadas por tipo"
          icono={ShieldAlert}
          mejorSiBaja
          href={enReportes(["infracciones"], AGRUPACION.infracciones)}
          {...datos(infracciones)}
        />
      </div>

      <Kpi
        tipo="tabla"
        titulo="Indicadores por empresa"
        icono={Table2}
        valor={null}
        href={tabla.kpis.length ? enReportes(tabla.kpis, EMPRESA) : undefined}
        {...(tabla.ok ? { tabla: tabla.tabla } : { error: tabla.mensaje })}
      />
    </div>
  );
}

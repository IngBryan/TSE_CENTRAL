import { BadgeCheck, Building2, Package, Route, ShieldAlert, Truck } from "lucide-react";

import Kpi from "@/components/Kpi";
import { requerirRol } from "@/lib/backoffice-sesion";
import { valoresFaceta } from "@/lib/facetas-backend";
import {
  cargaPorRubro,
  infraccionesPorTipo,
  totalDetectadas,
  totalToneladas,
  totalViajes,
  viajesPorPeriodo,
} from "@/lib/kpi";
import {
  consultaFiltros,
  hoyEnUruguay,
  leerFiltros,
  PERIODOS,
  PREDETERMINADOS_DASHBOARD,
  rangoComparacion,
  rangoPeriodo,
  tasaConfirmadas,
  textoRango,
  textoSeleccion,
  type FiltrosDashboard,
} from "@/lib/reportes";
import { dashboardConComparacion } from "@/lib/reportes-mock";
import BarraDashboard from "./BarraDashboard";

// This year against the same days of last year, unless the clock or the filters say otherwise
export default async function DashboardPage({ searchParams }: PageProps<"/dashboard">) {
  await requerirRol("AUTORIDAD");
  // Rubro is the only filter every KPI of the dashboard can take: companies and vehicles have no department or
  // cargo type
  const filtros: FiltrosDashboard = {
    ...leerFiltros(await searchParams, PREDETERMINADOS_DASHBOARD),
    tiposCarga: undefined,
    departamentos: undefined,
  };

  const hoy = hoyEnUruguay();
  const periodo = rangoPeriodo(filtros.periodo, filtros.rango, hoy);
  const comparacion = rangoComparacion(filtros.comparar, periodo, filtros.rangoComparar);
  // Test data until the backend has the reports
  const { actual: datos, anterior } = dashboardConComparacion(periodo, comparacion, filtros);
  const rubros = await valoresFaceta("category");
  const valores = rubros.ok ? rubros.valores : [];

  const nombreRubro = (id: string) => valores.find((r) => r.id === id)?.name ?? id;
  const filtrosActivos = [filtros.rubros && `Rubro: ${textoSeleccion(filtros.rubros, nombreRubro)}`].filter(Boolean);

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
          valor={datos.empresasHabilitadas}
          comparativo={anterior?.empresasHabilitadas}
        />
        <Kpi
          titulo="Vehículos habilitados"
          icono={Truck}
          valor={datos.vehiculosHabilitados}
          comparativo={anterior?.vehiculosHabilitados}
        />
        <Kpi
          titulo="Tasa de infracciones confirmadas sobre detectadas"
          icono={BadgeCheck}
          formato="porcentaje"
          valor={tasaConfirmadas(datos)}
          comparativo={anterior ? tasaConfirmadas(anterior) : null}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Kpi
          tipo="columnas"
          titulo="Viajes realizados por período"
          icono={Route}
          valor={totalViajes(datos)}
          comparativo={anterior ? totalViajes(anterior) : null}
          agrupacion={viajesPorPeriodo(datos, anterior)}
        />
        <Kpi
          tipo="columnas"
          titulo="Volumen de carga transportado por rubro"
          icono={Package}
          formato="toneladas"
          valor={totalToneladas(datos)}
          comparativo={anterior ? totalToneladas(anterior) : null}
          agrupacion={cargaPorRubro(datos, anterior)}
        />
        <Kpi
          tipo="columnas"
          titulo="Infracciones detectadas por tipo"
          icono={ShieldAlert}
          mejorSiBaja
          valor={totalDetectadas(datos)}
          comparativo={anterior ? totalDetectadas(anterior) : null}
          agrupacion={infraccionesPorTipo(datos, anterior)}
        />
      </div>
    </div>
  );
}

import type { CSSProperties } from "react";

import { SidebarProvider } from "@/components/ui/sidebar";
import { requerirRol } from "@/lib/backoffice-sesion";
import {
  consultaFiltros,
  hoyEnUruguay,
  leerFiltros,
  PERIODOS,
  rangoPeriodo,
  textoRango,
  textoSeleccion,
} from "@/lib/reportes";
import {
  COMPARATIVO_POR_DEFECTO,
  consultaReporte,
  describirReporte,
  leerReporte,
  reporteCompara,
  textoComparativo,
} from "@/lib/reportes-kpis";
import { tablaReporteMock, tiposCargaMock } from "@/lib/reportes-mock";
import ExportarReporte from "./ExportarReporte";
import PanelFiltros, { BotonConfigurar } from "./PanelFiltros";
import TablaReporte from "./TablaReporte";

// By default: trips of this month, grouped by month. The panel adds KPIs (columns), the grouping (rows),
// the period and the filters
export default async function ReportesPage({ searchParams }: PageProps<"/reportes">) {
  await requerirRol("AUTORIDAD");
  const parametros = await searchParams;
  const filtros = leerFiltros(parametros);
  const reporte = leerReporte(parametros);

  // One "today" for the whole render, so the header, the table and the panel use the same dates
  const hoy = hoyEnUruguay();
  const periodo = rangoPeriodo(filtros.periodo, filtros.rango, hoy);

  // One comparison for the whole report, only while some KPI shows more than the current value
  const comparativo = reporteCompara(reporte)
    ? { comparar: filtros.comparar ?? COMPARATIVO_POR_DEFECTO, rangoComparar: filtros.rangoComparar }
    : null;
  const contra = comparativo
    ? textoComparativo(comparativo.comparar, comparativo.rangoComparar ? textoRango(comparativo.rangoComparar) : null)
    : null;

  // Test data until the backend has the reports
  const tiposCarga = tiposCargaMock();
  const tabla = tablaReporteMock(reporte, periodo, filtros, comparativo);

  const nombreTipo = (id: string) => tiposCarga.find((t) => String(t.id) === id)?.nombre ?? id;
  const filtrosActivos = [
    filtros.tiposCarga && `Tipo de carga: ${textoSeleccion(filtros.tiposCarga, nombreTipo)}`,
    filtros.departamentos && `Departamento: ${textoSeleccion(filtros.departamentos, (d) => d)}`,
  ].filter((texto): texto is string => Boolean(texto));
  const descripcion = describirReporte(reporte, PERIODOS[filtros.periodo], textoRango(periodo), contra);

  // Same query as now, another page; changing the report in the panel goes back to the first one
  const consulta = `${consultaFiltros(filtros)}&${consultaReporte(reporte)}`;
  const pagina = Number(typeof parametros.pagina === "string" ? parametros.pagina : 1) || 1;
  const hrefPagina = (n: number) => `/reportes?${consulta}&pagina=${n}`;

  return (
    // Its own sidebar context for the settings panel: no cookie or Ctrl+B, those belong to the menu
    <SidebarProvider
      defaultOpen={false}
      cookieName={null}
      keyboardShortcut={null}
      className="min-h-0"
      style={{ "--sidebar-width": "20rem" } as CSSProperties}
    >
      <div className="w-full min-w-0 flex-1 space-y-6 px-6 py-8 lg:px-8">
        <header className="flex flex-wrap items-start justify-between gap-4">
          <div className="max-w-3xl">
            <h1 className="text-3xl font-semibold tracking-tight">Reportes</h1>
            <p className="mt-2 text-muted-foreground">{descripcion}</p>
            {filtrosActivos.length > 0 && (
              <p className="mt-1 text-sm text-muted-foreground">Filtros: {filtrosActivos.join(" · ")}</p>
            )}
          </div>

          <div className="flex gap-2">
            <BotonConfigurar />
            <ExportarReporte
              tabla={tabla}
              reporte={reporte}
              hoy={hoy}
              descripcion={descripcion}
              filtros={filtrosActivos}
            />
          </div>
        </header>

        <TablaReporte tabla={tabla} reporte={reporte} pagina={pagina} hrefPagina={hrefPagina} />
      </div>

      {/* Remounted on each search so the panel starts from the applied report */}
      <PanelFiltros
        key={consulta}
        filtros={filtros}
        reporte={reporte}
        tiposCarga={tiposCarga}
        hoy={hoy}
      />
    </SidebarProvider>
  );
}

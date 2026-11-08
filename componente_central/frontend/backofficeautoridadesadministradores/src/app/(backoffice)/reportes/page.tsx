import type { CSSProperties } from "react";

import { SidebarProvider } from "@/components/ui/sidebar";
import { requerirRol } from "@/lib/backoffice-sesion";
import { cargarReporte, facetasDeKpis, listarKpis, valoresFaceta } from "@/lib/reportes-backend";
import {
  consultaFiltros,
  hoyEnUruguay,
  leerFiltros,
  nombreValor,
  PERIODOS,
  rangoComparacion,
  rangoPeriodo,
  seleccionesDe,
  textoRango,
  textoSeleccion,
} from "@/lib/reportes";
import {
  agrupacionPermitida,
  COMPARATIVO_POR_DEFECTO,
  consultaReporte,
  describirReporte,
  esIdKpi,
  facetasDeFiltro,
  leerReporte,
  nombreFaceta,
  reporteCompara,
  sinComparar,
  textoComparativo,
  type CatalogosFaceta,
  type IdKpi,
  type NombresKpi,
} from "@/lib/reportes-kpis";
import { pedidoReporte, tablaReporte } from "@/lib/reportes-tabla";
import ExportarReporte from "./ExportarReporte";
import PanelFiltros, { BotonConfigurar } from "./PanelFiltros";
import TablaReporte from "./TablaReporte";

// By default: trips of this month, grouped by month. The panel adds KPIs (columns), the grouping (rows),
// the period and the filters
export default async function ReportesPage({ searchParams }: PageProps<"/reportes">) {
  await requerirRol("AUTORIDAD");
  const parametros = await searchParams;
  const leido = leerReporte(parametros);

  const [kpis, facetas] = await Promise.all([listarKpis(), facetasDeKpis(leido.kpis.map((kpi) => kpi.id))]);
  // Only what the KPIs of the report take: a link with a company grouped by department falls back to month, and
  // its department filter is dropped
  const listaFacetas = facetas.ok ? facetas.datos : null;
  const deFiltro = listaFacetas ? facetasDeFiltro(listaFacetas) : [];
  const leidos = leerFiltros(parametros);
  // The whole history has nothing to be compared with: only the current value
  const reporte = {
    agrupacion: agrupacionPermitida(leido.agrupacion, listaFacetas),
    kpis: leidos.periodo === "historico" ? sinComparar(leido.kpis) : leido.kpis,
  };
  const filtros = { ...leidos, selecciones: seleccionesDe(leidos.selecciones, deFiltro.map((faceta) => faceta.id)) };

  // The ones the front knows how to show
  const disponibles = kpis.ok
    ? kpis.datos.filter((kpi) => esIdKpi(kpi.id)).map((kpi) => ({ id: kpi.id as IdKpi, nombre: kpi.name }))
    : [];
  const nombres: NombresKpi = Object.fromEntries(disponibles.map((kpi) => [kpi.id, kpi.nombre]));

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

  const comparacion = comparativo && periodo ? rangoComparacion(comparativo.comparar, periodo, comparativo.rangoComparar) : null;

  // The report and the values of its filters at once
  const [cargado, ...respuestas] = await Promise.all([
    cargarReporte(pedidoReporte(reporte, periodo, comparacion, filtros)),
    ...deFiltro.map((faceta) => valoresFaceta(faceta.id)),
  ]);
  const catalogos: CatalogosFaceta = Object.fromEntries(
    deFiltro.map((faceta, i) => {
      const respuesta = respuestas[i];
      return [faceta.id, respuesta.ok ? { valores: respuesta.datos } : { valores: [], error: respuesta.mensaje }];
    })
  );
  const esTiempo = listaFacetas?.find((faceta) => faceta.id === reporte.agrupacion)?.kind === "time";
  const tabla = cargado.ok ? tablaReporte(cargado.datos, esTiempo) : null;

  const filtrosActivos = Object.entries(filtros.selecciones).map(
    ([faceta, seleccion]) =>
      `${nombreFaceta(listaFacetas, faceta)}: ${textoSeleccion(seleccion, nombreValor(catalogos[faceta]?.valores ?? []))}`
  );
  const descripcion = describirReporte(
    reporte,
    nombres,
    listaFacetas,
    PERIODOS[filtros.periodo],
    periodo ? textoRango(periodo) : null,
    contra
  );

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
            {tabla && (
              <ExportarReporte
                tabla={tabla}
                reporte={reporte}
                nombres={nombres}
                facetas={listaFacetas}
                hoy={hoy}
                descripcion={descripcion}
                filtros={filtrosActivos}
              />
            )}
          </div>
        </header>

        {tabla ? (
          <TablaReporte
            tabla={tabla}
            reporte={reporte}
            nombres={nombres}
            facetas={listaFacetas}
            pagina={pagina}
            hrefPagina={hrefPagina}
          />
        ) : (
          <p className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-6 text-center text-sm text-destructive">
            {cargado.ok ? null : cargado.mensaje}
          </p>
        )}
      </div>

      {/* Remounted on each search so the panel starts from the applied report */}
      <PanelFiltros
        key={consulta}
        filtros={filtros}
        reporte={reporte}
        kpisDisponibles={disponibles}
        errorKpis={kpis.ok ? undefined : kpis.mensaje}
        facetas={listaFacetas}
        errorFacetas={facetas.ok ? undefined : facetas.mensaje}
        catalogos={catalogos}
        hoy={hoy}
      />
    </SidebarProvider>
  );
}

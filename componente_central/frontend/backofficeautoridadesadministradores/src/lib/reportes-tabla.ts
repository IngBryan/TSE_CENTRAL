import { fechaCorta, type FiltrosDashboard, type Rango } from "./reportes";
import type { PedidoReporte, RespuestaReporte } from "./reportes-backend";
import type { IdKpi, Medida, Reporte } from "./reportes-kpis";

// What the table and the exports show: one row per group, each KPI with its measures; null is a measure without value
export type ValoresFila = Partial<Record<IdKpi, Partial<Record<Medida, number | null>>>>;
export type FilaReporte = { clave: string; etiqueta: string; valores: ValoresFila };
export type TablaReporte = { filas: FilaReporte[]; total: FilaReporte };

// Whole days: from the start of the first one to the end of the last one, Uruguay time
function rangoCerrado(rango: Rango) {
  return { from: `${rango.desde}T00:00:00`, to: `${rango.hasta}T23:59:59.999` };
}

export function pedidoReporte(
  reporte: Reporte,
  periodo: Rango | null,
  comparacion: Rango | null,
  filtros: FiltrosDashboard
): PedidoReporte {
  return {
    kpis: reporte.kpis.map((kpi) => ({ id: kpi.id, measures: kpi.medidas })),
    // Without a period, the backend takes the whole history
    period: periodo ? rangoCerrado(periodo) : null,
    comparison: comparacion ? rangoCerrado(comparacion) : null,
    groupBy: reporte.agrupacion,
    filters: Object.fromEntries(
      Object.entries(filtros.selecciones).flatMap(([faceta, seleccion]) =>
        seleccion ? [[faceta, { include: seleccion.incluir, exclude: seleccion.excluir }]] : []
      )
    ),
  };
}

const MES = new Intl.DateTimeFormat("es-UY", { month: "short", year: "numeric", timeZone: "UTC" });

// Time groups come as their ids: "2026-11-07", "2026-W45", "2026-11", "2026"
function etiquetaTiempo(id: string): string {
  if (/^\d{4}-\d{2}-\d{2}$/.test(id)) return fechaCorta(id);
  const semana = /^(\d{4})-W(\d{2})$/.exec(id);
  if (semana) return `Semana ${Number(semana[2])} de ${semana[1]}`;
  if (/^\d{4}-\d{2}$/.test(id)) return MES.format(new Date(`${id}-01T00:00:00Z`)).replace(".", "");
  return id;
}

// The rows in the order the backend gives them; the one without a value of the facet says so
export function tablaReporte(respuesta: RespuestaReporte, esTiempo: boolean): TablaReporte {
  return {
    filas: respuesta.rows.map((fila) => ({
      clave: fila.group?.id ?? "sin-valor",
      etiqueta: fila.group === null ? "Sin valor" : esTiempo ? etiquetaTiempo(fila.group.id) : fila.group.name,
      valores: fila.values as ValoresFila,
    })),
    total: { clave: "total", etiqueta: "Total", valores: respuesta.total as ValoresFila },
  };
}

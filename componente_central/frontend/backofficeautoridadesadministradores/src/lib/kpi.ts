import type { FormatoKpi } from "@/components/Kpi";

import type { RespuestaReporte, Resultado } from "./reportes-backend";

// One column of a KPI chart; comparativo is the same group in the compared period
export type Grupo = { clave: string; etiqueta: string; valor: number; comparativo?: number };

// A KPI of the dashboard as POST reports/load gives it: its total and, grouped, its columns
export type KpiDashboard =
  | { ok: true; valor: number | null; comparativo: number | null; grupos: Grupo[] }
  | { ok: false; mensaje: string };

const MES = new Intl.DateTimeFormat("es-UY", { month: "short", timeZone: "UTC" });

// "2026-03" -> "mar"; any other group keeps its name
function etiqueta(id: string, nombre: string): string {
  return /^\d{4}-\d{2}$/.test(id) ? MES.format(new Date(`${id}-01T00:00:00Z`)).replace(".", "") : nombre;
}

export function kpiDashboard(respuesta: Resultado<RespuestaReporte>, id: string, compara: boolean): KpiDashboard {
  if (!respuesta.ok) return { ok: false, mensaje: respuesta.mensaje };

  const { rows, total } = respuesta.datos;
  return {
    ok: true,
    valor: total[id]?.current ?? null,
    comparativo: compara ? (total[id]?.previous ?? null) : null,
    grupos: rows.map((fila) => ({
      clave: fila.group?.id ?? "sin-valor",
      etiqueta: fila.group ? etiqueta(fila.group.id, fila.group.name) : "Sin valor",
      valor: fila.values[id]?.current ?? 0,
      comparativo: compara ? (fila.values[id]?.previous ?? 0) : undefined,
    })),
  };
}

// Several indicators side by side: one column each, one row per group of the facet
export type ColumnaTabla = { id: string; titulo: string; formato: FormatoKpi; mejorSiBaja: boolean };
export type FilaTabla = {
  clave: string;
  etiqueta: string;
  valores: Record<string, { valor: number | null; comparativo: number | null }>;
};
export type TablaKpi = { encabezado: string; columnas: ColumnaTabla[]; filas: FilaTabla[] };

export type TablaDashboard = { ok: true; tabla: TablaKpi } | { ok: false; mensaje: string };

// The rows in the order the backend gives them (by the first indicator, the biggest first)
export function tablaDashboard(
  respuesta: Resultado<RespuestaReporte>,
  encabezado: string,
  columnas: ColumnaTabla[],
  compara: boolean
): TablaDashboard {
  if (!respuesta.ok) return { ok: false, mensaje: respuesta.mensaje };

  return {
    ok: true,
    tabla: {
      encabezado,
      columnas,
      filas: respuesta.datos.rows.map((fila) => ({
        clave: fila.group?.id ?? "sin-valor",
        etiqueta: fila.group?.name ?? "Sin valor",
        valores: Object.fromEntries(
          columnas.map((columna) => {
            const medidas = fila.values[columna.id];
            return [
              columna.id,
              { valor: medidas?.current ?? null, comparativo: compara ? (medidas?.previous ?? null) : null },
            ];
          })
        ),
      })),
    },
  };
}

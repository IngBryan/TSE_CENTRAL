"use server";

import { requerirRol } from "@/lib/backoffice-sesion";
import { facetasDeKpis, valoresFaceta } from "@/lib/reportes-backend";

// The facets the panel can offer for these KPIs, asked again each time a KPI is added or removed
export async function facetasPermitidas(kpis: string[]) {
  await requerirRol("AUTORIDAD");
  const ids = Array.isArray(kpis) ? kpis.filter((kpi) => typeof kpi === "string" && /^[a-z_]{1,40}$/.test(kpi)) : [];
  if (ids.length === 0) return { ok: false as const, mensaje: "Elegí al menos un KPI." };
  return facetasDeKpis(ids);
}

// The values of a filter the panel did not have yet: removing a KPI can bring back a facet
export async function valoresDeFaceta(faceta: string) {
  await requerirRol("AUTORIDAD");
  if (typeof faceta !== "string" || !/^[A-Za-z]{1,40}$/.test(faceta)) {
    return { ok: false as const, mensaje: "Filtro desconocido." };
  }
  return valoresFaceta(faceta);
}

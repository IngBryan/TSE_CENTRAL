"use client";

import { useActionState } from "react";
import { guardarPlazoDescargo } from "./plazo-actions";
import type { PlazoDescargo, EstadoPlazoDescargo } from "@/lib/plazo-descargo";

const inicial: EstadoPlazoDescargo = { estado: "inicial" };
const input = "mt-2 w-full rounded-md border bg-background px-3 py-2 disabled:opacity-60";

export default function FormularioPlazoDescargo({ configuracion }: { configuracion: PlazoDescargo }) {
  const [estado, action, pendiente] = useActionState(guardarPlazoDescargo, inicial);
  const actual = estado.configuracion ?? configuracion;
  return <form action={action} className="space-y-5">
    <input type="hidden" name="revisionBase" value={actual.revision} />
    <fieldset disabled={pendiente} className="space-y-5">
      <label htmlFor="plazo-descargo-dias" className="block max-w-sm text-sm font-medium">Plazo (días corridos)
        <input id="plazo-descargo-dias" className={input} name="plazoDescargoDias" type="number" min="1" max="365" step="1"
          required defaultValue={configuracion.plazoDescargoDias} aria-describedby="plazo-descargo-ayuda" />
      </label>
      <label htmlFor="fundamento-plazo" className="block text-sm font-medium">Fundamento del cambio
        <textarea id="fundamento-plazo" className={input} name="fundamento" required maxLength={500} rows={3}
          placeholder="Explicá el motivo del ajuste del plazo" />
      </label>
      <button type="submit" className="rounded-md bg-primary px-5 py-2 font-medium text-primary-foreground disabled:opacity-60">
        {pendiente ? "Guardando…" : "Guardar plazo de descargo"}
      </button>
    </fieldset>
    {estado.mensaje && <div role={estado.estado === "error" ? "alert" : "status"}
      className={`rounded-md border p-4 text-sm ${estado.estado === "error" ? "text-destructive" : ""}`}>
      {estado.mensaje}
      {estado.conflicto && <button type="button" className="ml-3 underline" onClick={() => window.location.reload()}>Recargar plazo</button>}
    </div>}
  </form>;
}

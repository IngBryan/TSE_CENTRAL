"use client";

import { useActionState } from "react";
import { guardarReglas } from "./actions";
import type { ConfiguracionReglas, EstadoReglas } from "@/lib/reglas-fiscalizacion";

const inicial: EstadoReglas = { estado: "inicial" };
const input = "mt-2 w-full rounded-md border bg-background px-3 py-2 disabled:opacity-60";

export default function FormularioReglas({ configuracion }: { configuracion: ConfiguracionReglas }) {
  const [estado, action, pendiente] = useActionState(guardarReglas, inicial);
  const actual = estado.configuracion ?? configuracion;
  return (
    <form action={action} className="space-y-6">
      <input type="hidden" name="revisionBase" value={actual.revision} />
      <fieldset disabled={pendiente} className="space-y-6">
        <section className="rounded-lg border p-6" aria-labelledby="carga-titulo">
          <h2 id="carga-titulo" className="text-xl font-semibold">Exceso de carga</h2>
          <p id="carga-ayuda" className="mt-2 text-sm text-muted-foreground">
            El peso base es el peso del vehículo más su capacidad de carga. Se permite el mayor de los dos márgenes siguientes.
            El caso se genera cuando el pesaje supera ese límite; igualarlo no genera infracción.
          </p>
          <div className="mt-5 grid gap-5 sm:grid-cols-2">
            <label htmlFor="porcentaje" className="text-sm font-medium">Tolerancia sobre el peso base (%)
              <input id="porcentaje" className={input} name="toleranciaCargaPorcentaje" type="number" min="0" max="20" step="0.01"
                required defaultValue={configuracion.toleranciaCargaPorcentaje} aria-describedby="carga-ayuda" />
              <span className="mt-1 block text-xs text-muted-foreground">De 0 a 20 %. Con 0 no hay margen porcentual.</span>
            </label>
            <label htmlFor="kg" className="text-sm font-medium">Margen absoluto (kg)
              <input id="kg" className={input} name="toleranciaCargaKg" type="number" min="0" max="5000" step="1"
                required defaultValue={configuracion.toleranciaCargaKg} aria-describedby="carga-ayuda" />
              <span className="mt-1 block text-xs text-muted-foreground">De 0 a 5000 kg. Los márgenes no se suman.</span>
            </label>
          </div>
          <p className="mt-4 rounded-md bg-muted p-3 text-sm">Ejemplo: base de 30.000 kg, tolerancia de 2 % y margen de 500 kg → límite de 30.600 kg.</p>
        </section>
        <section className="rounded-lg border p-6" aria-labelledby="desvio-titulo">
          <h2 id="desvio-titulo" className="text-xl font-semibold">Desvío de ruta</h2>
          <p id="desvio-ayuda" className="mt-2 text-sm text-muted-foreground">
            Se confirma con dos posiciones consecutivas de la misma Guía, ambas a una distancia mayor que la permitida
            de todas sus rutas autorizadas, dentro de la ventana indicada.
          </p>
          <div className="mt-5 grid gap-5 sm:grid-cols-2">
            <label htmlFor="metros" className="text-sm font-medium">Distancia permitida (metros)
              <input id="metros" className={input} name="distanciaDesvioMetros" type="number" min="1" max="10000" step="1"
                required defaultValue={configuracion.distanciaDesvioMetros} aria-describedby="desvio-ayuda" />
              <span className="mt-1 block text-xs text-muted-foreground">De 1 a 10.000 metros.</span>
            </label>
            <label htmlFor="segundos" className="text-sm font-medium">Ventana de confirmación (segundos)
              <input id="segundos" className={input} name="ventanaDesvioSegundos" type="number" min="1" max="600" step="1"
                required defaultValue={configuracion.ventanaDesvioSegundos} aria-describedby="desvio-ayuda" />
              <span className="mt-1 block text-xs text-muted-foreground">Separación máxima entre muestras: de 1 a 600 segundos.</span>
            </label>
          </div>
        </section>
        <label htmlFor="fundamento" className="block text-sm font-medium">Fundamento del cambio
          <textarea id="fundamento" className={input} name="fundamento" required maxLength={500} rows={3}
            placeholder="Explicá el motivo del ajuste" />
        </label>
        <button type="submit" className="rounded-md bg-primary px-5 py-2 font-medium text-primary-foreground disabled:opacity-60">
          {pendiente ? "Guardando…" : "Guardar configuración"}
        </button>
      </fieldset>
      {estado.mensaje && <div role={estado.estado === "error" ? "alert" : "status"}
        className={`rounded-md border p-4 text-sm ${estado.estado === "error" ? "text-destructive" : ""}`}>
        {estado.mensaje}
        {estado.conflicto && <button type="button" className="ml-3 underline" onClick={() => window.location.reload()}>Recargar configuración</button>}
      </div>}
    </form>
  );
}

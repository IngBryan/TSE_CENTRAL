"use client";

import { useActionState, useState } from "react";
import { botonGubUy, botonSecundario } from "@/components/encabezado";
import { crearGuia, type EstadoNuevaGuia } from "./actions";

const CAMPOS = [
  { nombre: "rubroCliente", etiqueta: "Rubro del cliente", tipo: "text", ayuda: "Por ejemplo: agropecuario, forestal, construcción." },
  { nombre: "pesoCargaKg", etiqueta: "Carga (kg)", tipo: "number" },
  { nombre: "fecha", etiqueta: "Fecha del viaje", tipo: "date" },
  { nombre: "origen", etiqueta: "Origen", tipo: "text" },
  { nombre: "destino", etiqueta: "Destino", tipo: "text" },
] as const;

export function NuevaGuia({ hoy }: { hoy: string }) {
  const [abierto, setAbierto] = useState(false);
  const [estado, accion, guardando] = useActionState<EstadoNuevaGuia, FormData>(async (previo, datos) => {
    const r = await crearGuia(previo, datos);
    if (r.ok) setAbierto(false);
    return r;
  }, {});

  if (!abierto) {
    return (
      <div className="flex flex-col gap-3">
        {estado.ok && (
          <p role="status" className="rounded-lg bg-green-50 p-4 text-green-900 dark:bg-green-950 dark:text-green-200">
            Guardamos la guía {estado.ok}. Ahora la podés asignar a un vehículo y un chofer.
          </p>
        )}
        <button type="button" onClick={() => setAbierto(true)} className={`${botonGubUy} self-start`}>
          Nueva guía
        </button>
      </div>
    );
  }

  return (
    <form action={accion} className="flex flex-col gap-4 rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950">
      <h2 className="text-lg font-semibold text-black dark:text-zinc-50">Nueva guía de viaje</h2>
      {estado.error && (
        <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
          {estado.error}
        </p>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        {CAMPOS.map((c) => {
          const error = estado.campos?.[c.nombre];
          return (
            <label
              key={c.nombre}
              className={`flex flex-col gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300 ${c.nombre === "rubroCliente" ? "sm:col-span-2" : ""}`}
            >
              {c.etiqueta}
              <input
                name={c.nombre}
                type={c.tipo}
                defaultValue={estado.valores?.[c.nombre] ?? ""}
                required
                min={c.tipo === "number" ? 1 : c.tipo === "date" ? hoy : undefined}
                step={c.tipo === "number" ? 1 : undefined}
                aria-invalid={error ? true : undefined}
                className={`h-10 rounded-lg border bg-transparent px-3 text-base font-normal text-black dark:text-zinc-50 ${
                  error ? "border-red-500" : "border-black/15 dark:border-white/20"
                }`}
              />
              {error ? (
                <span className="text-xs font-normal text-red-700 dark:text-red-300">{error}</span>
              ) : (
                "ayuda" in c && <span className="text-xs font-normal text-zinc-500">{c.ayuda}</span>
              )}
            </label>
          );
        })}
      </div>
      <div className="flex gap-3">
        <button type="submit" disabled={guardando} className={botonGubUy}>
          {guardando ? "Guardando…" : "Guardar guía"}
        </button>
        <button type="button" onClick={() => setAbierto(false)} disabled={guardando} className={botonSecundario}>
          Cancelar
        </button>
      </div>
    </form>
  );
}

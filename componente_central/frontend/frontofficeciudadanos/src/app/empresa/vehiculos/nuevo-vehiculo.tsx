"use client";

import { useActionState, useState } from "react";
import { botonGubUy, botonSecundario } from "@/components/estilos";
import { crearVehiculo, type EstadoNuevoVehiculo } from "./actions";

const CAMPOS = [
  { nombre: "matricula", etiqueta: "Matrícula", tipo: "text", ayuda: "Por ejemplo SBA1234. Después no se puede cambiar." },
  { nombre: "marca", etiqueta: "Marca", tipo: "text" },
  { nombre: "modelo", etiqueta: "Modelo", tipo: "text" },
  { nombre: "peso", etiqueta: "Peso del vehículo (kg)", tipo: "number", ayuda: "Peso en vacío (tara)." },
  { nombre: "capacidadCargaKg", etiqueta: "Capacidad de carga (kg)", tipo: "number" },
] as const;

export function NuevoVehiculo() {
  const [abierto, setAbierto] = useState(false);
  const [estado, accion, guardando] = useActionState<EstadoNuevoVehiculo, FormData>(async (previo, datos) => {
    const r = await crearVehiculo(previo, datos);
    if (r.ok) setAbierto(false);
    return r;
  }, {});

  if (!abierto) {
    return (
      <div className="flex flex-col gap-3">
        {estado.ok && (
          <p role="status" className="rounded-lg bg-green-50 p-4 text-green-900 dark:bg-green-950 dark:text-green-200">
            Registramos el vehículo {estado.ok}. Cargale el permiso de circulación y la ITV para que quede habilitado.
          </p>
        )}
        <button type="button" onClick={() => setAbierto(true)} className={`${botonGubUy} self-start`}>
          Nuevo vehículo
        </button>
      </div>
    );
  }

  return (
    <form action={accion} className="flex flex-col gap-4 rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950">
      <h2 className="text-lg font-semibold text-black dark:text-zinc-50">Nuevo vehículo</h2>
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
              className={`flex flex-col gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300 ${c.nombre === "matricula" ? "sm:col-span-2" : ""}`}
            >
              {c.etiqueta}
              <input
                name={c.nombre}
                type={c.tipo}
                defaultValue={estado.valores?.[c.nombre] ?? ""}
                required
                min={c.tipo === "number" ? 1 : undefined}
                step={c.tipo === "number" ? 1 : undefined}
                autoCapitalize={c.nombre === "matricula" ? "characters" : undefined}
                aria-invalid={error ? true : undefined}
                className={`h-10 rounded-lg border bg-transparent px-3 text-base font-normal text-black dark:text-zinc-50 ${
                  error ? "border-red-500" : "border-black/15 dark:border-white/20"
                } ${c.nombre === "matricula" ? "uppercase" : ""}`}
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
          {guardando ? "Guardando…" : "Guardar vehículo"}
        </button>
        <button type="button" onClick={() => setAbierto(false)} disabled={guardando} className={botonSecundario}>
          Cancelar
        </button>
      </div>
    </form>
  );
}

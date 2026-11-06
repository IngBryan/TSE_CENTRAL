"use client";

import { useActionState } from "react";
import { botonGubUy } from "@/components/estilos";
import { actualizarEmpresa, type DatosEmpresa, type EstadoFormulario } from "./actions";

const CAMPOS = [
  { nombre: "nombrePublico", etiqueta: "Nombre público", max: 100 },
  { nombre: "razonSocial", etiqueta: "Razón social", max: 100 },
  { nombre: "direccion", etiqueta: "Dirección principal", max: 200 },
] as const;

export function FormularioEmpresa({ empresa }: { empresa: DatosEmpresa }) {
  const [estado, accion, guardando] = useActionState<EstadoFormulario, FormData>(actualizarEmpresa, {});
  const valores = estado.valores ?? empresa;

  return (
    <form action={accion} className="flex flex-col gap-5 rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950">
      {estado.ok && (
        <p role="status" className="rounded-lg bg-green-50 p-4 text-green-900 dark:bg-green-950 dark:text-green-200">
          Guardamos los datos de la empresa.
        </p>
      )}
      {estado.error && (
        <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
          {estado.error}
        </p>
      )}

      <div className="flex flex-col gap-1">
        <span className="text-sm font-medium text-zinc-700 dark:text-zinc-300">Nro. de empresa</span>
        <span className="tabular-nums text-black dark:text-zinc-50">{empresa.nroEmpresa}</span>
        <span className="text-xs text-zinc-500">Lo asigna el MTOP y no se puede modificar.</span>
      </div>

      <div className="flex flex-col gap-1">
        <span className="text-sm font-medium text-zinc-700 dark:text-zinc-300">Rubros</span>
        {empresa.rubros?.length ? (
          <ul className="flex flex-wrap gap-2">
            {empresa.rubros.map((r) => (
              <li
                key={r}
                className="rounded-full bg-zinc-100 px-3 py-1 text-sm text-zinc-800 dark:bg-zinc-800 dark:text-zinc-200"
              >
                {r}
              </li>
            ))}
          </ul>
        ) : (
          <span className="text-zinc-600 dark:text-zinc-400">Sin rubros asignados.</span>
        )}
        <span className="text-xs text-zinc-500">Los asigna el MTOP y no se pueden modificar.</span>
      </div>

      {CAMPOS.map((c) => {
        const error = estado.campos?.[c.nombre];
        return (
          <label key={c.nombre} className="flex flex-col gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300">
            {c.etiqueta}
            <input
              // key: al guardar se re-monta con el valor que devolvio el backend (ya normalizado)
              key={`${c.nombre}-${valores[c.nombre]}`}
              name={c.nombre}
              defaultValue={valores[c.nombre] ?? ""}
              maxLength={c.max}
              required
              aria-invalid={error ? true : undefined}
              className={`h-10 rounded-lg border bg-transparent px-3 text-base font-normal text-black dark:text-zinc-50 ${
                error ? "border-red-500" : "border-black/15 dark:border-white/20"
              }`}
            />
            {error && <span className="text-xs font-normal text-red-700 dark:text-red-300">{error}</span>}
          </label>
        );
      })}

      <button type="submit" disabled={guardando} className={`${botonGubUy} self-start`}>
        {guardando ? "Guardando…" : "Guardar cambios"}
      </button>
    </form>
  );
}

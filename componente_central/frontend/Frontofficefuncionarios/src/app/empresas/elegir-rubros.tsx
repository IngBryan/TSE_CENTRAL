"use client";

import type { ResultadoRubros } from "./actions";

const entrada =
  "h-10 rounded-lg border border-black/15 bg-transparent px-3 text-base font-normal text-black disabled:opacity-60 dark:border-white/20 dark:bg-zinc-950 dark:text-zinc-50";

// Seleccion de los rubros de la empresa (alta y modificacion): se elige de una lista desplegable y cada
// rubro elegido se agrega abajo (y deja de estar en la lista); con la x se quita. El catalogo es precargado
// y la empresa puede tener varios o ninguno. Que los ids existan lo valida el backend.
export function ElegirRubros({
  catalogo,
  elegidos,
  onCambiar,
  deshabilitado,
  error,
}: {
  catalogo: ResultadoRubros;
  elegidos: number[];
  onCambiar: (ids: number[]) => void;
  deshabilitado?: boolean;
  error?: string;
}) {
  const rubros = catalogo.ok ? catalogo.rubros : [];
  const disponibles = rubros.filter((r) => !elegidos.includes(r.id));
  // En el orden del catalogo (por nombre), no en el que se fueron eligiendo
  const seleccionados = rubros.filter((r) => elegidos.includes(r.id));

  return (
    <div className="flex flex-col gap-2">
      <label htmlFor="rubros" className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
        Rubros
      </label>
      {!catalogo.ok ? (
        <p role="alert" className="text-sm text-red-700 dark:text-red-300">
          {catalogo.error}
        </p>
      ) : (
        <select
          id="rubros"
          // Siempre vuelve a "Agregar un rubro…": elegir una opcion la agrega a la lista de abajo
          value=""
          disabled={deshabilitado || disponibles.length === 0}
          onChange={(e) => {
            const id = Number(e.target.value);
            if (id) onCambiar([...elegidos, id]);
          }}
          className={entrada}
        >
          <option value="" disabled>
            {rubros.length === 0
              ? "No hay rubros cargados en el sistema"
              : disponibles.length === 0
                ? "Ya elegiste todos los rubros"
                : "Agregar un rubro…"}
          </option>
          {disponibles.map((r) => (
            <option key={r.id} value={r.id}>
              {r.nombre}
            </option>
          ))}
        </select>
      )}

      {seleccionados.length > 0 ? (
        <ul className="flex flex-wrap gap-2">
          {seleccionados.map((r) => (
            <li
              key={r.id}
              className="flex items-center gap-1 rounded-full bg-zinc-100 py-1 pl-3 pr-1 text-sm text-zinc-800 dark:bg-zinc-800 dark:text-zinc-200"
            >
              {r.nombre}
              <button
                type="button"
                disabled={deshabilitado}
                onClick={() => onCambiar(elegidos.filter((id) => id !== r.id))}
                aria-label={`Quitar ${r.nombre}`}
                className="flex h-5 w-5 items-center justify-center rounded-full text-zinc-500 hover:bg-zinc-200 hover:text-black disabled:opacity-60 dark:hover:bg-zinc-700 dark:hover:text-white"
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      ) : (
        catalogo.ok && <span className="text-sm text-zinc-500">Sin rubros elegidos.</span>
      )}
      <span className="text-xs font-normal text-zinc-500">Podés elegir varios. Son opcionales.</span>
      {error && <span className="text-xs font-normal text-red-700 dark:text-red-300">{error}</span>}
    </div>
  );
}

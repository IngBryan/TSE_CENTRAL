
import Form from "next/form";
import Link from "next/link";

import {
  NOMBRE_ROL,
  type FiltrosUsuarios as Filtros,
  type RolUsuario,
} from "@/lib/usuarios";

/**
 * Filtro por rol y buscador por cédula. Se envía por GET: los filtros
 * quedan en la URL y la búsqueda vuelve a la primera página.
 */
export default function FiltrosUsuarios({ filtros }: { filtros: Filtros }) {
  const hayFiltros = filtros.rol !== null || filtros.ci !== "";

  return (
    <Form
      action="/usuarios"
      role="search"
      className="flex flex-wrap items-end gap-3 rounded-lg border p-4"
    >
      <label className="flex flex-col gap-1 text-sm">
        <span className="font-medium">Cédula</span>
        <input
          type="search"
          name="ci"
          defaultValue={filtros.ci}
          inputMode="numeric"
          maxLength={20}
          placeholder="Ej. 5407438"
          className="w-48 rounded-md border bg-background px-3 py-2"
        />
      </label>

      <label className="flex flex-col gap-1 text-sm">
        <span className="font-medium">Rol</span>
        <select
          name="rol"
          defaultValue={filtros.rol ?? ""}
          className="w-56 rounded-md border bg-background px-3 py-2"
        >
          <option value="">Todos los roles</option>
          {(Object.keys(NOMBRE_ROL) as RolUsuario[]).map((rol) => (
            <option key={rol} value={rol}>
              {NOMBRE_ROL[rol]}
            </option>
          ))}
        </select>
      </label>

      <button
        type="submit"
        className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
      >
        Buscar
      </button>

      {hayFiltros && (
        <Link
          href="/usuarios"
          className="rounded-md border px-4 py-2 text-sm hover:bg-muted"
        >
          Limpiar filtros
        </Link>
      )}
    </Form>
  );
}

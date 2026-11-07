"use client";

import type { LocalidadVista } from "@/lib/localidades";

type Props = {
  nombre: "origenId" | "destinoId";
  etiqueta: string;
  localidades: LocalidadVista[];
  valor?: string | number | null;
  onChange?: (id: number | null) => void;
  error?: string;
  deshabilitado?: boolean;
};

export function SelectorLocalidad({ nombre, etiqueta, localidades, valor, onChange, error, deshabilitado }: Props) {
  return (
    <label className="flex flex-col gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300">
      {etiqueta}
      <select
        name={nombre}
        required
        value={valor ?? ""}
        onChange={e => onChange?.(e.target.value ? Number(e.target.value) : null)}
        disabled={deshabilitado || localidades.length === 0}
        aria-invalid={error ? true : undefined}
        className={`h-10 rounded-lg border bg-white px-3 text-base font-normal text-black dark:bg-zinc-950 dark:text-zinc-50 ${
          error ? "border-red-500" : "border-black/15 dark:border-white/20"
        }`}
      >
        <option value="">Seleccionar localidad…</option>
        {localidades.map(l => (
          <option key={l.id} value={l.id}>{l.nombre} ({l.departamento})</option>
        ))}
      </select>
      {error && <span className="text-xs font-normal text-red-700 dark:text-red-300">{error}</span>}
    </label>
  );
}

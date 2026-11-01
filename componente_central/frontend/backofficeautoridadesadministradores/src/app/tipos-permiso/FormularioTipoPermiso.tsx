
"use client";

import { useState, useTransition } from "react";

import type { TipoPermiso } from "@/lib/tipos-permiso-backend";
import { guardarTipoPermisoAction } from "./actions";

const VACIO = {
  nombre: "",
  pideNumero: true,
  pideVencimiento: true,
  activo: true,
};

/**
 * Alta de un tipo de permiso o, si recibe uno, su modificación.
 * Desactivar un tipo deja de exigirlo: no hay baja física.
 */
export default function FormularioTipoPermiso({
  tipo,
  alTerminar,
}: {
  tipo?: TipoPermiso;
  alTerminar?: () => void;
}) {
  const [datos, setDatos] = useState(
    tipo
      ? {
          nombre: tipo.nombre,
          pideNumero: tipo.pideNumero,
          pideVencimiento: tipo.pideVencimiento,
          activo: tipo.activo,
        }
      : VACIO
  );
  const [error, setError] = useState<string | null>(null);
  const [pendiente, iniciar] = useTransition();

  const guardar = (evento: React.FormEvent) => {
    evento.preventDefault();
    setError(null);

    iniciar(async () => {
      const resultado = await guardarTipoPermisoAction(datos, tipo?.id);

      if (!resultado.ok) {
        setError(resultado.mensaje);
        return;
      }

      if (!tipo) {
        setDatos(VACIO);
      }

      alTerminar?.();
    });
  };

  return (
    <form onSubmit={guardar} className="space-y-3">
      <label className="block text-sm font-medium">
        Nombre
        <input
          value={datos.nombre}
          maxLength={100}
          required
          onChange={(e) => setDatos({ ...datos, nombre: e.target.value })}
          className="mt-1 w-full rounded-md border bg-transparent px-3 py-2 font-normal"
        />
      </label>

      <div className="flex flex-wrap gap-x-6 gap-y-2 text-sm">
        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={datos.pideNumero}
            onChange={(e) =>
              setDatos({ ...datos, pideNumero: e.target.checked })
            }
          />
          Pide número
        </label>

        <label className="flex items-center gap-2">
          <input
            type="checkbox"
            checked={datos.pideVencimiento}
            onChange={(e) =>
              setDatos({ ...datos, pideVencimiento: e.target.checked })
            }
          />
          Pide vencimiento
        </label>

        {tipo && (
          <label className="flex items-center gap-2">
            <input
              type="checkbox"
              checked={datos.activo}
              onChange={(e) =>
                setDatos({ ...datos, activo: e.target.checked })
              }
            />
            Activo
          </label>
        )}
      </div>

      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}

      <div className="flex gap-3">
        <button
          type="submit"
          disabled={pendiente}
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
        >
          {pendiente ? "Guardando…" : tipo ? "Guardar" : "Crear tipo de permiso"}
        </button>

        {tipo && (
          <button
            type="button"
            disabled={pendiente}
            onClick={alTerminar}
            className="rounded-md border px-4 py-2 text-sm hover:bg-muted"
          >
            Cancelar
          </button>
        )}
      </div>
    </form>
  );
}

"use client";

import { useState, useTransition } from "react";
import { darDeBajaChofer } from "./actions";

// Boton "Quitar" de la fila de un chofer, con confirmacion. La baja es logica: el chofer deja de estar en la
// empresa y no se lo puede asignar a viajes, pero se lo puede volver a agregar con "Agregar chofer".
export function QuitarChofer({ cedula, nombre }: { cedula: string; nombre: string }) {
  const [confirmar, setConfirmar] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pendiente, iniciar] = useTransition();

  const quitar = () => {
    setError(null);
    iniciar(async () => {
      const r = await darDeBajaChofer(cedula);
      if (r.ok) {
        setConfirmar(false);
      } else {
        setError(r.mensaje);
        setConfirmar(false);
      }
    });
  };

  return (
    <div className="flex flex-col items-end gap-1">
      {confirmar ? (
        <div className="flex flex-wrap items-center justify-end gap-2">
          <span className="text-xs text-zinc-600 dark:text-zinc-400">¿Quitar a {nombre} de la empresa?</span>
          <button
            type="button"
            onClick={quitar}
            disabled={pendiente}
            className="rounded-full bg-red-600 px-3 py-1 text-xs font-medium text-white hover:bg-red-700 disabled:opacity-50"
          >
            {pendiente ? "Quitando…" : "Sí, quitar"}
          </button>
          <button
            type="button"
            onClick={() => setConfirmar(false)}
            disabled={pendiente}
            className="rounded-full border border-black/10 px-3 py-1 text-xs font-medium hover:bg-black/5 dark:border-white/15 dark:hover:bg-white/10"
          >
            No
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => {
            setError(null);
            setConfirmar(true);
          }}
          className="rounded-full border border-red-200 px-3 py-1 text-xs font-medium text-red-700 hover:bg-red-50 dark:border-red-900 dark:text-red-300 dark:hover:bg-red-950"
        >
          Quitar
        </button>
      )}
      {error && (
        <span role="alert" className="max-w-64 text-right text-xs text-red-700 dark:text-red-300">
          {error}
        </span>
      )}
    </div>
  );
}

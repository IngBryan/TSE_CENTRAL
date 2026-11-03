"use client";

import { useActionState, useState } from "react";
import { botonGubUy } from "@/components/estilos";
import { MAX_DESCARGO } from "@/lib/infracciones";
import { presentarDescargo, type EstadoDescargo } from "../actions";

// Formulario del descargo. Se presenta una sola vez: despues la pagina muestra el descargo presentado.
export function PresentarDescargo({ idCaso, vence }: { idCaso: number; vence: string }) {
  const [estado, accion, enviando] = useActionState<EstadoDescargo, FormData>(
    presentarDescargo.bind(null, idCaso),
    {},
  );
  const [largo, setLargo] = useState(0);

  return (
    <form action={accion} className="flex flex-col gap-3 rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950">
      <h2 className="text-lg font-semibold text-black dark:text-zinc-50">Presentar descargo</h2>
      <p className="text-sm text-zinc-600 dark:text-zinc-400">
        Explicá por qué la empresa no está de acuerdo con la infracción. Lo revisa un funcionario del MTOP antes de
        resolver el caso. Se puede presentar una sola vez. Plazo: hasta el {vence} inclusive.
      </p>
      {estado.error && (
        <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
          {estado.error}
        </p>
      )}
      <label className="flex flex-col gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300">
        Descargo
        <textarea
          name="descripcion"
          required
          rows={6}
          maxLength={MAX_DESCARGO}
          defaultValue={estado.texto ?? ""}
          onChange={(e) => setLargo(e.target.value.length)}
          aria-invalid={estado.campo ? true : undefined}
          className="rounded-lg border border-black/15 bg-transparent p-3 text-base font-normal dark:border-white/20"
        />
        <span className="flex justify-between text-xs font-normal text-zinc-500">
          <span className="text-red-700 dark:text-red-300">{estado.campo}</span>
          <span>
            {largo}/{MAX_DESCARGO}
          </span>
        </span>
      </label>
      <button type="submit" disabled={enviando} className={`${botonGubUy} self-start`}>
        {enviando ? "Enviando…" : "Presentar descargo"}
      </button>
    </form>
  );
}

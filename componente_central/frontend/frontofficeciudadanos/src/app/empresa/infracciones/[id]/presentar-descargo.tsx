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
    <form action={accion} className="flex flex-col gap-3 rounded-xl border bg-card p-6 shadow-sm">
      <h2 className="text-lg font-semibold text-foreground">Presentar descargo</h2>
      <p className="text-sm text-muted-foreground">
        Explicá por qué la empresa no está de acuerdo con la infracción. Lo revisa un funcionario del MTOP antes de
        resolver el caso. Se puede presentar una sola vez. Plazo: hasta el {vence} inclusive.
      </p>
      {estado.error && (
        <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
          {estado.error}
        </p>
      )}
      <label className="flex flex-col gap-1 text-sm font-medium text-foreground/80">
        Descargo
        <textarea
          name="descripcion"
          required
          rows={6}
          maxLength={MAX_DESCARGO}
          defaultValue={estado.texto ?? ""}
          onChange={(e) => setLargo(e.target.value.length)}
          aria-invalid={estado.campo ? true : undefined}
          className="rounded-lg border border-input bg-transparent p-3 text-base font-normal"
        />
        <span className="flex justify-between text-xs font-normal text-muted-foreground">
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

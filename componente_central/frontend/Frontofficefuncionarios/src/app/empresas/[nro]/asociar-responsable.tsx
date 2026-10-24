"use client";

import { useState, useTransition } from "react";
import { botonPrimario, botonSecundario } from "@/components/encabezado";
import { nombreUsuario, type UsuarioDisponible } from "@/lib/empresas";
import { asociarResponsable, type ResultadoUsuarios } from "../actions";
import { ElegirResponsable } from "../elegir-responsable";

// FA4: a una empresa sin responsable el funcionario le asocia uno, elegido entre los usuarios registrados y
// validados que no son responsables de otra empresa. Lo abre AccionesEmpresa; si sale bien, revalidatePath
// en la action vuelve a renderizar la pagina con el responsable nuevo.
export function AsociarResponsable({
  nroEmpresa,
  nombreEmpresa,
  disponibles,
  onCerrar,
  onAsociado,
}: {
  nroEmpresa: number;
  nombreEmpresa: string | null;
  disponibles: ResultadoUsuarios;
  onCerrar: () => void;
  onAsociado: (mensaje: string) => void;
}) {
  const [elegido, setElegido] = useState<UsuarioDisponible | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pendiente, iniciar] = useTransition();

  const confirmar = () => {
    if (!elegido) return;
    setError(null);
    iniciar(async () => {
      const r = await asociarResponsable(nroEmpresa, elegido.ci);
      if (r.ok) {
        onAsociado(r.mensaje);
      } else {
        setError(r.error);
        setElegido(null);
      }
    });
  };

  return (
    <div className="flex flex-col gap-4">
      {error && (
        <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
          {error}
        </p>
      )}

      <ElegirResponsable
        inicial={disponibles}
        elegido={elegido}
        onElegir={(u) => {
          setElegido(u);
          setError(null);
        }}
        deshabilitado={pendiente}
        titulo="Elegir responsable"
      />

      {elegido && (
        <p className="text-sm text-zinc-700 dark:text-zinc-300">
          ¿Asociar a {nombreUsuario(elegido)} (C.I. {elegido.ci}) como responsable de la empresa N° {nroEmpresa}
          {nombreEmpresa ? ` "${nombreEmpresa}"` : ""}?
        </p>
      )}

      <div className="flex gap-3">
        <button type="button" disabled={!elegido || pendiente} onClick={confirmar} className={botonPrimario}>
          {pendiente ? "Asociando…" : "Confirmar"}
        </button>
        <button type="button" disabled={pendiente} onClick={onCerrar} className={botonSecundario}>
          Cancelar
        </button>
      </div>
    </div>
  );
}

"use client";

import { useState, useTransition } from "react";
import { botonPrimario, botonSecundario } from "@/components/encabezado";
import type { EmpresaDetalle } from "@/lib/empresas";
import { modificarEmpresa, type ResultadoRubros, type ResultadoUsuarios } from "../actions";
import { AsociarResponsable } from "./asociar-responsable";
import { ModificarEmpresa } from "./modificar-empresa";

type Panel = "modificar" | "asociar" | null;

// Acciones sobre la empresa: modificar sus datos (FA3) y, si no tiene responsable, asociarle uno (FA4). Los
// botones van lado a lado y se abre un panel a la vez. Al asociar el responsable de una empresa no habilitada
// se ofrece habilitarla, que antes no se podia (sin responsable no se habilita).
export function AccionesEmpresa({
  empresa,
  disponibles,
  rubros,
  creada,
}: {
  empresa: EmpresaDetalle;
  // Solo si la empresa no tiene responsable: la primera tanda de usuarios que se pueden asociar
  disponibles: ResultadoUsuarios | null;
  // Catalogo de rubros, para modificar los de la empresa
  rubros: ResultadoRubros;
  // Viene del alta (?creada=1). Es el mensaje inicial: la siguiente accion lo reemplaza, aunque la URL lo
  // siga teniendo
  creada: boolean;
}) {
  const [panel, setPanel] = useState<Panel>(null);
  const [exito, setExito] = useState<string | null>(creada ? "La empresa fue registrada." : null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [ofrecerHabilitar, setOfrecerHabilitar] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pendiente, iniciar] = useTransition();

  const abrir = (p: Panel) => {
    setExito(null);
    setAviso(null);
    setError(null);
    setOfrecerHabilitar(false);
    setPanel(p);
  };

  const habilitar = () => {
    setError(null);
    iniciar(async () => {
      // Se reenvian los datos actuales: el PUT de la empresa los pide todos. Con la version, si otro funcionario
      // los cambio mientras tanto el backend responde 409 en vez de volver a escribir los viejos.
      const r = await modificarEmpresa(empresa.nroEmpresa, {
        nombrePublico: empresa.nombrePublico ?? "",
        razonSocial: empresa.razonSocial ?? "",
        direccion: empresa.direccion ?? "",
        habilitada: true,
        version: empresa.version ?? 0,
      });
      if (r.ok) {
        setExito("La empresa fue habilitada.");
        setOfrecerHabilitar(false);
      } else if (r.desactualizada) {
        // La pagina ya se recargo con los datos actuales: si sigue sin habilitar, se puede volver a intentar
        setError(`${r.error} Revisalos y volvé a intentar.`);
      } else {
        setError(r.campos?.habilitada ?? r.error);
      }
    });
  };

  if (panel === "modificar") {
    return (
      <ModificarEmpresa
        empresa={empresa}
        rubros={rubros}
        onCerrar={() => setPanel(null)}
        onModificada={(mensaje) => {
          setExito(mensaje);
          setPanel(null);
        }}
        onDesactualizada={(mensaje) => {
          setAviso(mensaje);
          setPanel(null);
        }}
      />
    );
  }

  if (panel === "asociar" && disponibles) {
    return (
      <AsociarResponsable
        nroEmpresa={empresa.nroEmpresa}
        nombreEmpresa={empresa.nombrePublico}
        disponibles={disponibles}
        onCerrar={() => setPanel(null)}
        onAsociado={(mensaje) => {
          setExito(mensaje);
          setOfrecerHabilitar(!empresa.habilitada);
          setPanel(null);
        }}
        onYaTieneResponsable={(mensaje) => {
          setAviso(`${mensaje} Arriba se muestra el responsable actual.`);
          setPanel(null);
        }}
      />
    );
  }

  return (
    <div className="flex flex-col gap-3">
      {exito && (
        <p role="status" className="rounded-lg bg-emerald-50 p-3 text-sm text-emerald-900 dark:bg-emerald-950 dark:text-emerald-200">
          {exito}
        </p>
      )}

      {aviso && (
        <p role="alert" className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900 dark:bg-amber-950 dark:text-amber-200">
          {aviso}
        </p>
      )}

      {ofrecerHabilitar && !empresa.habilitada && (
        <div className="flex flex-col gap-3 rounded-2xl border border-black/10 bg-white p-6 shadow-sm dark:border-white/15 dark:bg-zinc-950">
          <p className="text-sm text-zinc-700 dark:text-zinc-300">
            La empresa ya tiene responsable. ¿Querés habilitarla ahora? Solo una empresa habilitada puede dar de
            alta choferes.
          </p>
          {error && (
            <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
              {error}
            </p>
          )}
          <div className="flex gap-3">
            <button type="button" disabled={pendiente} onClick={habilitar} className={botonPrimario}>
              {pendiente ? "Habilitando…" : "Habilitar empresa"}
            </button>
            <button
              type="button"
              disabled={pendiente}
              onClick={() => setOfrecerHabilitar(false)}
              className={botonSecundario}
            >
              Ahora no
            </button>
          </div>
        </div>
      )}

      <div className="flex flex-wrap gap-3">
        <button type="button" onClick={() => abrir("modificar")} className={botonPrimario}>
          Modificar datos
        </button>
        {disponibles && (
          <button type="button" onClick={() => abrir("asociar")} className={botonPrimario}>
            Asociar responsable
          </button>
        )}
      </div>
    </div>
  );
}

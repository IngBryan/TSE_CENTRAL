"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { botonGubUy, botonSecundario } from "@/components/estilos";
import { registrarPermisoExtra, type PermisoExtraVista } from "../actions";
import { formatoFecha } from "../estado";

const campo = "h-10 rounded-lg border border-black/15 bg-transparent px-3 text-base font-normal text-black dark:border-white/20 dark:text-zinc-50";
const etiqueta = "flex flex-col gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300";

// Permisos a demanda que un funcionario le exige a la empresa: el vehiculo no esta habilitado hasta tenerlos
// todos vigentes. Dado de baja (editable false) solo se muestran.
export function PermisosExtra({ matricula, permisos, editable }: { matricula: string; permisos: PermisoExtraVista[]; editable: boolean }) {
  return (
    <section className="flex flex-col gap-3 rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950">
      <h2 className="text-lg font-semibold text-black dark:text-zinc-50">Permisos adicionales</h2>
      <p className="text-sm text-zinc-600 dark:text-zinc-400">Los exige el MTOP a tu empresa, además del permiso de circulación y la ITV.</p>
      <ul className="flex flex-col divide-y divide-black/10 dark:divide-white/15">
        {permisos.map((p) => (
          <PermisoExtra key={p.idTipo} matricula={matricula} permiso={p} editable={editable} />
        ))}
      </ul>
    </section>
  );
}

function PermisoExtra({ matricula, permiso: p, editable }: { matricula: string; permiso: PermisoExtraVista; editable: boolean }) {
  const router = useRouter();
  const [abierto, setAbierto] = useState(false);
  const [numero, setNumero] = useState(p.numero ?? "");
  const [vencimiento, setVencimiento] = useState(p.fechaVencimiento ?? "");
  const [error, setError] = useState<string | null>(null);
  const [campos, setCampos] = useState<Record<string, string>>({});
  const [pendiente, iniciar] = useTransition();

  const guardar = () => {
    setError(null);
    iniciar(async () => {
      const r = await registrarPermisoExtra(matricula, p.idTipo, { numero, fechaVencimiento: vencimiento });
      if (r.ok) {
        setAbierto(false);
        setCampos({});
        router.refresh();
      } else {
        setError(r.error);
        setCampos(r.campos ?? {});
      }
    });
  };

  const estado = !p.cargado ? (
    <span className="text-red-700 dark:text-red-300">Sin cargar</span>
  ) : !p.vigente ? (
    <span className="text-red-700 dark:text-red-300">Venció {p.fechaVencimiento && formatoFecha(p.fechaVencimiento)}</span>
  ) : (
    <span className="text-green-700 dark:text-green-300">
      {p.fechaVencimiento ? `Vence ${formatoFecha(p.fechaVencimiento)}` : "Vigente"}
    </span>
  );

  return (
    <li className="flex flex-col gap-3 py-3 text-sm first:pt-0 last:pb-0">
      <div className="flex flex-wrap items-center gap-3">
        <span className="mr-auto flex flex-col">
          <span className="font-medium text-black dark:text-zinc-50">{p.nombre}</span>
          {p.numero && <span className="font-mono text-xs">{p.numero}</span>}
        </span>
        {estado}
        {editable && !abierto && (
          <button type="button" onClick={() => setAbierto(true)} className={`${botonSecundario} h-9 px-4`}>
            {p.cargado ? "Actualizar" : "Cargar"}
          </button>
        )}
      </div>

      {abierto && (
        <div className="flex flex-col gap-3">
          {error && (
            <p role="alert" className="rounded-lg bg-red-50 p-3 text-red-800 dark:bg-red-950 dark:text-red-200">
              {error}
            </p>
          )}
          <div className="grid gap-4 sm:grid-cols-2">
            {p.pideNumero && (
              <label className={etiqueta}>
                Número de permiso
                <input value={numero} maxLength={30} onChange={(e) => setNumero(e.target.value)} className={`${campo} uppercase`} />
                {campos.numero && <span className="text-xs font-normal text-red-700 dark:text-red-300">{campos.numero}</span>}
              </label>
            )}
            {p.pideVencimiento && (
              <label className={etiqueta}>
                Válido hasta
                <input type="date" value={vencimiento} onChange={(e) => setVencimiento(e.target.value)} className={campo} />
                {campos.fechaVencimiento && (
                  <span className="text-xs font-normal text-red-700 dark:text-red-300">{campos.fechaVencimiento}</span>
                )}
              </label>
            )}
          </div>
          <div className="flex gap-3">
            <button type="button" disabled={pendiente} onClick={guardar} className={botonGubUy}>
              {pendiente ? "Guardando…" : "Guardar"}
            </button>
            <button type="button" disabled={pendiente} onClick={() => setAbierto(false)} className={botonSecundario}>
              Volver
            </button>
          </div>
        </div>
      )}
    </li>
  );
}

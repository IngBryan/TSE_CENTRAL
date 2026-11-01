"use client";

import { useState, useTransition } from "react";
import { botonPrimario } from "@/components/encabezado";
import type { PermisoEmpresa } from "@/lib/empresas";
import { asignarPermisos } from "../actions";

// Permisos a demanda que se le exigen a la empresa, ademas del Permiso Nacional y la ITV. Los crea un
// administrador en el backoffice; el responsable los carga en cada vehiculo.
export function PermisosEmpresa({ nroEmpresa, permisos }: { nroEmpresa: number; permisos: PermisoEmpresa[] | null }) {
  const [elegidos, setElegidos] = useState(() => (permisos ?? []).filter((p) => p.asignado).map((p) => p.id));
  const [mensaje, setMensaje] = useState<{ ok: boolean; texto: string } | null>(null);
  const [pendiente, iniciar] = useTransition();

  const alternar = (id: number, marcado: boolean) => {
    setMensaje(null);
    setElegidos(marcado ? [...elegidos, id] : elegidos.filter((e) => e !== id));
  };

  const guardar = () => {
    setMensaje(null);
    iniciar(async () => {
      const r = await asignarPermisos(nroEmpresa, elegidos);
      setMensaje(r.ok ? { ok: true, texto: r.mensaje } : { ok: false, texto: r.error });
    });
  };

  return (
    <section className="flex flex-col gap-3 rounded-2xl bg-white p-6 text-sm shadow-sm dark:bg-zinc-950">
      <div>
        <h2 className="text-lg font-semibold text-black dark:text-zinc-50">Permisos adicionales</h2>
        <p className="text-zinc-600 dark:text-zinc-400">
          Sus vehículos los tienen que tener vigentes, además del Permiso Nacional de Circulación y la ITV.
        </p>
      </div>

      {permisos === null ? (
        <p className="text-red-800 dark:text-red-200">No se pudieron obtener los permisos.</p>
      ) : permisos.length === 0 ? (
        <p className="text-zinc-600 dark:text-zinc-400">No hay tipos de permiso activos en el backoffice.</p>
      ) : (
        <>
          <ul className="flex flex-col gap-2">
            {permisos.map((p) => (
              <li key={p.id}>
                <label className="flex items-center gap-2">
                  <input type="checkbox" checked={elegidos.includes(p.id)} onChange={(e) => alternar(p.id, e.target.checked)} />
                  {p.nombre}
                </label>
              </li>
            ))}
          </ul>
          {mensaje && (
            <p
              role={mensaje.ok ? "status" : "alert"}
              className={
                mensaje.ok
                  ? "rounded-lg bg-emerald-50 p-3 text-emerald-900 dark:bg-emerald-950 dark:text-emerald-200"
                  : "rounded-lg bg-red-50 p-3 text-red-800 dark:bg-red-950 dark:text-red-200"
              }
            >
              {mensaje.texto}
            </p>
          )}
          <div>
            <button type="button" disabled={pendiente} onClick={guardar} className={botonPrimario}>
              {pendiente ? "Guardando…" : "Guardar permisos"}
            </button>
          </div>
        </>
      )}
    </section>
  );
}

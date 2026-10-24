"use client";

import { useEffect, useRef, useState } from "react";
import { botonSecundario } from "@/components/encabezado";
import { nombreUsuario, type UsuarioDisponible } from "@/lib/empresas";
import { buscarUsuariosDisponibles, type ResultadoUsuarios } from "./actions";

const entrada =
  "h-10 rounded-lg border border-black/15 bg-transparent px-3 text-base font-normal text-black disabled:opacity-60 dark:border-white/20 dark:text-zinc-50";
const etiqueta = "flex flex-col gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300";

// Buscador de usuarios registrados y validados que todavia no son responsables de ninguna empresa. Lo usan
// el alta (FA1) y la asociacion del responsable a una empresa que no lo tiene (FA4).
export function ElegirResponsable({
  inicial,
  elegido,
  onElegir,
  deshabilitado,
  error,
  titulo = "Responsable (opcional)",
}: {
  inicial: ResultadoUsuarios;
  elegido: UsuarioDisponible | null;
  onElegir: (u: UsuarioDisponible | null) => void;
  deshabilitado: boolean;
  error?: string;
  titulo?: string;
}) {
  const [filtro, setFiltro] = useState("");
  const [resultado, setResultado] = useState<ResultadoUsuarios>(inicial);
  const [buscando, setBuscando] = useState(false);
  // Descarta respuestas de busquedas viejas que lleguen despues de una mas nueva
  const ultima = useRef(0);
  const espera = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useEffect(() => () => clearTimeout(espera.current), []);

  // Busca 300 ms despues de la ultima tecla; sin filtro vuelve a la primera tanda que trajo la pagina
  const buscar = (valor: string) => {
    setFiltro(valor);
    clearTimeout(espera.current);
    const id = ++ultima.current;
    if (valor === "") {
      setResultado(inicial);
      setBuscando(false);
      return;
    }
    espera.current = setTimeout(async () => {
      setBuscando(true);
      let r: ResultadoUsuarios;
      try {
        r = await buscarUsuariosDisponibles(valor);
      } catch {
        r = { ok: false, error: "No se pudieron obtener los usuarios registrados." };
      }
      if (id === ultima.current) {
        setResultado(r);
        setBuscando(false);
      }
    }, 300);
  };

  return (
    <section className="flex flex-col gap-4 rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950">
      <div className="flex flex-col gap-1">
        <h2 className="text-lg font-semibold text-black dark:text-zinc-50">{titulo}</h2>
        <p className="text-sm text-zinc-600 dark:text-zinc-400">
          Elegí un usuario registrado y validado con la PDI que no sea responsable de otra empresa.
        </p>
      </div>

      {elegido ? (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-[#0068b3] bg-[#0068b3]/5 p-3 text-sm">
          <span>
            <span className="font-medium text-black dark:text-zinc-50">{nombreUsuario(elegido)}</span>
            <span className="text-zinc-500"> · C.I. <span className="font-mono">{elegido.ci}</span></span>
          </span>
          <button type="button" disabled={deshabilitado} onClick={() => onElegir(null)} className={botonSecundario}>
            Quitar
          </button>
        </div>
      ) : (
        <>
          <label className={etiqueta}>
            Buscar por cédula
            <input
              value={filtro}
              inputMode="numeric"
              maxLength={8}
              disabled={deshabilitado}
              placeholder="Comienzo de la cédula"
              onChange={(e) => buscar(e.target.value.replace(/\D/g, ""))}
              className={`${entrada} max-w-64`}
            />
          </label>

          {buscando ? (
            <p className="text-sm text-zinc-500">Buscando…</p>
          ) : !resultado.ok ? (
            <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
              {resultado.error}
            </p>
          ) : resultado.usuarios.length === 0 ? (
            <p className="text-sm text-zinc-600 dark:text-zinc-400">
              {filtro ? "No hay usuarios disponibles con esa cédula." : "No hay usuarios disponibles."}
            </p>
          ) : (
            <ul className="flex max-h-72 flex-col divide-y divide-black/5 overflow-y-auto rounded-lg border border-black/10 dark:divide-white/10 dark:border-white/15">
              {resultado.usuarios.map((u) => (
                <li key={u.ci}>
                  <button
                    type="button"
                    disabled={deshabilitado}
                    onClick={() => onElegir(u)}
                    className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-zinc-50 disabled:opacity-60 dark:hover:bg-zinc-900"
                  >
                    <span className="text-black dark:text-zinc-50">{nombreUsuario(u)}</span>
                    <span className="font-mono text-zinc-500">C.I. {u.ci}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </>
      )}

      {error && <span className="text-xs text-red-700 dark:text-red-300">{error}</span>}
    </section>
  );
}

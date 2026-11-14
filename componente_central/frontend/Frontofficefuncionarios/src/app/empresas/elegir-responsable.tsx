"use client";

import { useEffect, useRef, useState } from "react";
import { botonSecundario } from "@/components/encabezado";
import { MAX_DISPONIBLES, nombreUsuario, type UsuarioDisponible } from "@/lib/empresas";
import { buscarUsuariosDisponibles, type ResultadoUsuarios } from "./actions";

const entrada =
  "h-10 rounded-lg border border-input bg-transparent px-3 text-base font-normal text-foreground disabled:opacity-60";
const etiqueta = "flex flex-col gap-1 text-sm font-medium text-foreground/80";

// Buscador de usuarios registrados y validados que todavia no son responsables de ninguna empresa. Lo usan
// el alta (FA1) y la asociacion del responsable a una empresa que no lo tiene (FA4).
export function ElegirResponsable({
  inicial,
  elegido,
  onElegir,
  deshabilitado,
  error,
  titulo = "Responsable (opcional)",
  excluidos = [],
}: {
  inicial: ResultadoUsuarios;
  elegido: UsuarioDisponible | null;
  onElegir: (u: UsuarioDisponible | null) => void;
  deshabilitado: boolean;
  error?: string;
  titulo?: string;
  // Cedulas que el backend ya rechazo en esta pantalla (dejaron de estar disponibles): no se vuelven a ofrecer
  // aunque sigan en la primera tanda que trajo la pagina
  excluidos?: string[];
}) {
  const [filtro, setFiltro] = useState("");
  const [resultado, setResultado] = useState<ResultadoUsuarios>(inicial);
  const [buscando, setBuscando] = useState(false);
  // Descarta respuestas de busquedas viejas que lleguen despues de una mas nueva
  const ultima = useRef(0);
  const espera = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useEffect(() => () => clearTimeout(espera.current), []);

  const usuarios = resultado.ok ? resultado.usuarios.filter((u) => !excluidos.includes(u.ci)) : [];
  // El backend devuelve hasta MAX_DISPONIBLES: si llego al tope puede haber mas (se cuenta antes de excluir)
  const cortada = resultado.ok && resultado.usuarios.length >= MAX_DISPONIBLES;

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
    <section className="flex flex-col gap-4 rounded-xl border bg-card p-6 shadow-sm">
      <div className="flex flex-col gap-1">
        <h2 className="text-lg font-semibold text-foreground">{titulo}</h2>
        <p className="text-sm text-muted-foreground">
          Elegí un usuario registrado y validado con la PDI que no sea responsable de otra empresa.
        </p>
      </div>

      {elegido ? (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-primary bg-primary/5 p-3 text-sm">
          <span>
            <span className="font-medium text-foreground">{nombreUsuario(elegido)}</span>
            <span className="text-muted-foreground"> · C.I. <span className="font-mono">{elegido.ci}</span></span>
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
            <p className="text-sm text-muted-foreground">Buscando…</p>
          ) : !resultado.ok ? (
            <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
              {resultado.error}
            </p>
          ) : usuarios.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              {filtro ? "No hay usuarios disponibles con esa cédula." : "No hay usuarios disponibles."}
            </p>
          ) : (
            <>
              <ul className="flex max-h-72 flex-col divide-y divide-border overflow-y-auto rounded-lg border border-border">
                {usuarios.map((u) => (
                  <li key={u.ci}>
                    <button
                      type="button"
                      disabled={deshabilitado}
                      onClick={() => onElegir(u)}
                      className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-muted/60 disabled:opacity-60"
                    >
                      <span className="text-foreground">{nombreUsuario(u)}</span>
                      <span className="font-mono text-muted-foreground">C.I. {u.ci}</span>
                    </button>
                  </li>
                ))}
              </ul>
              {cortada && (
                <p className="text-xs text-muted-foreground">
                  Se muestran los primeros {MAX_DISPONIBLES}. Si no encontrás al usuario, filtrá por cédula.
                </p>
              )}
            </>
          )}
        </>
      )}

      {error && <span className="text-xs text-red-700 dark:text-red-300">{error}</span>}
    </section>
  );
}

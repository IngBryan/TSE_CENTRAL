"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, useTransition } from "react";
import {
  marcarNotificacionVista,
  marcarTodasVistas,
  obtenerNotificaciones,
} from "@/app/empresa/infracciones/notificaciones";
import { fecha, fechaHora, tipoInfraccion, type Notificaciones } from "@/lib/infracciones";

// Cada cuanto se vuelve a consultar si hay casos nuevos, mientras la pestaña esta visible
const INTERVALO_MS = 60_000;

// Campana de notificaciones del encabezado (responsables de empresa): avisa los casos de fiscalizacion
// notificados a la empresa. Al hacer click en una notificacion queda vista y se abre la infraccion, desde
// donde se presenta el descargo.
export function Campana({ inicial }: { inicial: Notificaciones }) {
  const [datos, setDatos] = useState(inicial);
  const [abierta, setAbierta] = useState(false);
  const [pendiente, iniciar] = useTransition();
  const router = useRouter();
  const contenedor = useRef<HTMLDivElement>(null);

  // Lo que trae el servidor al navegar (por ejemplo, despues de abrir una infraccion) reemplaza lo local
  const [previo, setPrevio] = useState(inicial);
  if (previo !== inicial) {
    setPrevio(inicial);
    setDatos(inicial);
  }

  useEffect(() => {
    const id = setInterval(async () => {
      if (document.visibilityState !== "visible") return;
      const nuevas = await obtenerNotificaciones().catch(() => null);
      if (nuevas) setDatos(nuevas);
    }, INTERVALO_MS);
    return () => clearInterval(id);
  }, []);

  useEffect(() => {
    if (!abierta) return;
    const fuera = (e: MouseEvent) => {
      if (!contenedor.current?.contains(e.target as Node)) setAbierta(false);
    };
    const escape = (e: KeyboardEvent) => e.key === "Escape" && setAbierta(false);
    document.addEventListener("mousedown", fuera);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("mousedown", fuera);
      document.removeEventListener("keydown", escape);
    };
  }, [abierta]);

  const abrir = (id: number) =>
    iniciar(async () => {
      const nuevas = await marcarNotificacionVista(id);
      if (nuevas) setDatos(nuevas);
      setAbierta(false);
      router.push(`/empresa/infracciones/${id}`);
    });

  const todasVistas = () =>
    iniciar(async () => {
      const nuevas = await marcarTodasVistas();
      if (nuevas) setDatos(nuevas);
      router.refresh();
    });

  const { noVistas, notificaciones } = datos;
  return (
    <div ref={contenedor} className="relative">
      <button
        type="button"
        onClick={() => setAbierta((a) => !a)}
        aria-expanded={abierta}
        aria-haspopup="true"
        aria-label={noVistas > 0 ? `Notificaciones, ${noVistas} sin ver` : "Notificaciones"}
        className="relative flex h-10 w-10 items-center justify-center rounded-full text-zinc-700 transition-colors hover:bg-black/5 dark:text-zinc-300 dark:hover:bg-white/10"
      >
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.8} className="h-6 w-6" aria-hidden>
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M14.857 17.082a23.848 23.848 0 0 0 5.454-1.31A8.967 8.967 0 0 1 18 9.75V9A6 6 0 0 0 6 9v.75a8.967 8.967 0 0 1-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 0 1-5.714 0m5.714 0a3 3 0 1 1-5.714 0"
          />
        </svg>
        {noVistas > 0 && (
          <span className="absolute right-0.5 top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-600 px-1 text-xs font-semibold text-white">
            {noVistas > 9 ? "9+" : noVistas}
          </span>
        )}
      </button>

      {abierta && (
        <div className="absolute right-0 z-20 mt-2 w-[min(22rem,calc(100vw-2rem))] overflow-hidden rounded-2xl border border-black/5 bg-white shadow-lg dark:border-white/10 dark:bg-zinc-950">
          <div className="flex items-center justify-between gap-2 border-b border-black/5 px-4 py-3 dark:border-white/10">
            <p className="font-semibold text-black dark:text-zinc-50">Notificaciones</p>
            {noVistas > 0 && (
              <button
                type="button"
                onClick={todasVistas}
                disabled={pendiente}
                className="text-sm text-[#0068b3] hover:underline disabled:opacity-50"
              >
                Marcar todas como vistas
              </button>
            )}
          </div>

          {notificaciones.length === 0 ? (
            <p className="px-4 py-6 text-sm text-zinc-500">No tenés notificaciones.</p>
          ) : (
            <ul className="max-h-96 overflow-y-auto">
              {notificaciones.map((n) => (
                <li key={n.id} className="border-b border-black/5 last:border-0 dark:border-white/10">
                  <button
                    type="button"
                    onClick={() => abrir(n.id)}
                    disabled={pendiente}
                    className={`flex w-full gap-3 px-4 py-3 text-left text-sm transition-colors hover:bg-black/5 disabled:opacity-60 dark:hover:bg-white/10 ${n.vista ? "" : "bg-sky-50/70 dark:bg-sky-950/40"}`}
                  >
                    <span
                      className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${n.vista ? "bg-transparent" : "bg-[#0068b3]"}`}
                      aria-hidden
                    />
                    <span className="flex flex-col gap-0.5">
                      <span className={n.vista ? "text-zinc-700 dark:text-zinc-300" : "font-medium text-black dark:text-zinc-50"}>
                        {n.vista ? "" : <span className="sr-only">Sin ver: </span>}
                        Infracción por {tipoInfraccion(n.tipoCaso).toLowerCase()}
                        {n.matricula ? ` · ${n.matricula}` : ""}
                      </span>
                      <span className="text-xs text-zinc-500">
                        {n.admiteDescargo && n.venceDescargo
                          ? `Podés presentar el descargo hasta el ${fecha(n.venceDescargo)} inclusive`
                          : `Notificada el ${fechaHora(n.fecha)}`}
                      </span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          )}

          <Link
            href="/empresa/infracciones"
            onClick={() => setAbierta(false)}
            className="block border-t border-black/5 px-4 py-3 text-center text-sm text-[#0068b3] hover:bg-black/5 dark:border-white/10 dark:hover:bg-white/10"
          >
            Ver todas las infracciones
          </Link>
        </div>
      )}
    </div>
  );
}

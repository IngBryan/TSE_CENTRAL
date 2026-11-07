"use client";

import { useEffect, useRef, useState } from "react";
import { BASE_PATH } from "@/lib/base-path";
import { fechaHora } from "@/lib/casos";
import { MODIFICACIONES, TIPOS_EVENTO, nombreChofer,
  type EstadoViaje, type EventoVista, type ModificacionVista } from "@/lib/viajes";

const INTERVALO_MS = 5000;
const TAMANIO = 10;
type Datos = { estado: EstadoViaje; eventos: EventoVista[]; modificaciones: ModificacionVista[] };
type Entrada = { cuando: string | null; evento?: EventoVista; modificacion?: ModificacionVista };
const normalizar = (datos: Datos) => ({ ...datos,
  eventos: [...datos.eventos].sort((a,b) => (a.fechaHoraDispositivo ?? "").localeCompare(b.fechaHoraDispositivo ?? "")),
  modificaciones: [...datos.modificaciones].sort((a,b) => (a.fecha ?? "").localeCompare(b.fecha ?? "")),
});

/** Paginación del historial existente y actualización sin recarga de eventos del chofer. */
export function LineaTiempoEnVivo({ id, estadoInicial, eventosIniciales, modificacionesIniciales }: {
  id: string; estadoInicial: EstadoViaje; eventosIniciales: EventoVista[]; modificacionesIniciales: ModificacionVista[];
}) {
  const [datos, setDatos] = useState<Datos>(() => normalizar({
    estado: estadoInicial, eventos: eventosIniciales, modificaciones: modificacionesIniciales,
  }));
  const [pagina, setPagina] = useState(1);
  const [activo, setActivo] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [nuevos, setNuevos] = useState(0);
  const actuales = useRef<Datos>(datos);
  const enVuelo = useRef<AbortController | null>(null);
  const estado = datos.estado;

  useEffect(() => {
    if (!activo || estado === "FINALIZADA") return;
    let montado = true;
    const consultar = async () => {
      if (document.hidden || enVuelo.current) return;
      const ctrl = new AbortController();
      enVuelo.current = ctrl;
      try {
        const res = await fetch(`${BASE_PATH}/viajes/${encodeURIComponent(id)}/linea-tiempo-viva`, {
          cache: "no-store", signal: ctrl.signal,
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const respuesta = await res.json() as Datos;
        if (!Array.isArray(respuesta.eventos) || !Array.isArray(respuesta.modificaciones)) throw new Error("Respuesta inesperada");
        if (!montado || ctrl.signal.aborted) return;
        const siguiente = normalizar(respuesta);
        const previo = actuales.current;
        if (JSON.stringify(siguiente) !== JSON.stringify(previo)) {
          setNuevos(n => n + Math.max(0, siguiente.eventos.length - previo.eventos.length) +
            Math.max(0, siguiente.modificaciones.length - previo.modificaciones.length));
          actuales.current = siguiente;
          setDatos(siguiente);
        }
        setError(null);
      } catch (e) {
        if (montado && !ctrl.signal.aborted) setError((e as Error).message);
      } finally {
        if (enVuelo.current === ctrl) enVuelo.current = null;
      }
    };
    const timer = window.setInterval(() => { void consultar(); }, INTERVALO_MS);
    const alVolver = () => { if (!document.hidden) void consultar(); };
    document.addEventListener("visibilitychange", alVolver);
    void consultar();
    return () => { montado = false; window.clearInterval(timer);
      document.removeEventListener("visibilitychange", alVolver);
      enVuelo.current?.abort(); enVuelo.current = null; };
  }, [id, activo, estado]);

  const linea: Entrada[] = [
    ...datos.modificaciones.map(m => ({ cuando: m.fecha, modificacion: m })),
    ...datos.eventos.map(e => ({ cuando: e.fechaHoraDispositivo, evento: e })),
  ].sort((a,b) => (a.cuando ?? "").localeCompare(b.cuando ?? ""));
  const totalPaginas = Math.max(1, Math.ceil(linea.length / TAMANIO));
  const paginaActual = Math.min(pagina, totalPaginas);
  const visibles = linea.slice((paginaActual - 1) * TAMANIO, paginaActual * TAMANIO);
  return (
      <section className="flex flex-col gap-3">
        <h2 className="text-lg font-semibold text-black dark:text-zinc-50">Línea de tiempo</h2>
        <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-zinc-500">
          <span role="status" aria-live="polite">{estado === "FINALIZADA" ? "Historial del viaje" : activo ? "Actualización automática cada 5 s" : "Actualización pausada"}
            {nuevos > 0 ? ` · ${nuevos} entradas nuevas` : ""}</span>
          {estado !== "FINALIZADA" && <button type="button" onClick={() => setActivo(a => !a)}
            className="rounded-lg border px-3 py-1.5 hover:bg-zinc-100 dark:hover:bg-zinc-900">
            {activo ? "Pausar" : "Reanudar"}</button>}
        </div>
        {error && <p role="alert" className="text-xs text-amber-700 dark:text-amber-300">
          No se pudo consultar novedades ({error}); se conservan los eventos anteriores.
        </p>}
        {linea.length === 0 ? (
          <p className="text-zinc-600 dark:text-zinc-400">El chofer no registró eventos en este viaje.</p>
        ) : (
          <ol className="flex flex-col gap-3">
            {visibles.map(({ evento: e, modificacion: m }, i) =>
              m ? (
                <li key={`mod-${m.fecha}-${i}`} className="rounded-xl border-l-4 border-[#0068b3] bg-white p-4 text-sm shadow-sm dark:bg-zinc-950">
                  <div className="flex flex-wrap items-baseline justify-between gap-2">
                    <span className="font-medium text-black dark:text-zinc-50">{MODIFICACIONES[m.tipo] ?? m.tipo}</span>
                    <span className="text-xs text-zinc-500">
                      {fechaHora(m.fecha)} · responsable C.I. {m.ciResponsable}
                    </span>
                  </div>
                  {m.cambios && <p className="mt-1">{m.cambios}</p>}
                  {m.motivo && <p className="mt-1">Motivo: {m.motivo}</p>}
                </li>
              ) : e ? (
                <li
                  key={e.idEvento}
                  className={`rounded-xl bg-white p-4 text-sm shadow-sm dark:bg-zinc-950 ${
                    e.resultado === "CONFLICTO" ? "border-l-4 border-amber-500" : ""
                  }`}
                >
                  <div className="flex flex-wrap items-baseline justify-between gap-2">
                    <span className="font-medium text-black dark:text-zinc-50">{TIPOS_EVENTO[e.tipo] ?? e.tipo}</span>
                    <span className="text-xs text-zinc-500">
                      Ocurrió {fechaHora(e.fechaHoraDispositivo)} · recibido {fechaHora(e.fechaRecepcion)}
                    </span>
                  </div>
                  {e.descripcion && <p className="mt-1 whitespace-pre-line">{e.descripcion}</p>}
                  <p className="mt-1 text-xs text-zinc-500">
                    Chofer: {nombreChofer(e.chofer)}
                    {e.latitud != null && e.longitud != null && (
                      <>
                        {" · "}
                        <a
                          className="underline"
                          href={`https://www.openstreetmap.org/?mlat=${e.latitud}&mlon=${e.longitud}#map=14/${e.latitud}/${e.longitud}`}
                          target="_blank"
                          rel="noreferrer"
                        >
                          ver ubicación
                        </a>
                      </>
                    )}
                  </p>
                  {e.resultado === "CONFLICTO" && (
                    <p className="mt-2 text-amber-800 dark:text-amber-300">
                      En conflicto: {e.detalle ?? e.codigo}. No cambió el estado de la guía.
                    </p>
                  )}
                  {e.resultado === "SIN_EFECTO" && <p className="mt-2 text-zinc-500">Sin efecto: {e.detalle ?? e.codigo}</p>}
                  {e.conFoto && (
                    // La sirve el route handler de esta app (el navegador no tiene la API key): lleva el basePath
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={`${BASE_PATH}/viajes/${encodeURIComponent(id)}/eventos/${encodeURIComponent(e.idEvento)}/foto`}
                      alt={`Foto del ${(TIPOS_EVENTO[e.tipo] ?? e.tipo).toLowerCase()}`}
                      loading="lazy"
                      className="mt-3 max-h-80 rounded-lg"
                    />
                  )}
                </li>
              ) : null,
            )}
          </ol>
        )}
        {linea.length > 0 && <nav className="flex flex-wrap items-center justify-between gap-2 text-xs" aria-label="Paginación de línea de tiempo">
          <span>{linea.length} entradas · Página {paginaActual} de {totalPaginas}</span>
          <div className="flex gap-2">
            <button type="button" disabled={paginaActual === 1} onClick={() => setPagina(x => x - 1)}
              className="rounded-lg border px-3 py-1.5 disabled:opacity-40">Anterior</button>
            <button type="button" disabled={paginaActual === totalPaginas} onClick={() => setPagina(x => x + 1)}
              className="rounded-lg border px-3 py-1.5 disabled:opacity-40">Siguiente</button>
          </div>
        </nav>}
      </section>
  );
}

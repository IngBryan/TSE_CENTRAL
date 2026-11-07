"use client";

import { useEffect, useRef, useState } from "react";
import { BASE_PATH } from "@/lib/base-path";
import type { CirculacionVista, EventoTrackingVista, PruebaDesvioVista, EstadoCaso } from "@/lib/casos";
import { EventosTrackingPaginados } from "./eventos-tracking-paginados";
import { GeografiaCaso } from "./geografia-caso";

const INTERVALO_MS = 5000;
const MAPA_INTERVALO_MS = 30000;
type Snapshot = { eventosTracking: EventoTrackingVista[]; pruebasDesvio: PruebaDesvioVista[] };
const normalizar = (datos: Snapshot): Snapshot => ({
  eventosTracking: [...datos.eventosTracking].sort((a, b) => a.fechaHora.localeCompare(b.fechaHora) || a.matricula.localeCompare(b.matricula)),
  pruebasDesvio: [...datos.pruebasDesvio].sort((a, b) => a.fechaHora.localeCompare(b.fechaHora) || a.fechaPareja.localeCompare(b.fechaPareja)),
});
const hashSnapshot = (datos: Snapshot) => JSON.stringify(datos);

/** Una sola consulta periódica actualiza los eventos y el mapa, sin recargar el expediente ni
 * alterar la paginación, la confirmación seleccionada o las acciones administrativas. */
export function EvidenciasEnVivo({ id, tipo, guiaId, fechaDeteccion, estadoCaso, circulacion, eventosIniciales, pruebasIniciales }: {
  id: number;
  tipo: string;
  guiaId: string | null;
  fechaDeteccion: string | null;
  estadoCaso: EstadoCaso | null;
  circulacion: CirculacionVista | null;
  eventosIniciales: EventoTrackingVista[];
  pruebasIniciales: PruebaDesvioVista[];
}) {
  const [datos, setDatos] = useState<Snapshot>(() => normalizar({ eventosTracking: eventosIniciales, pruebasDesvio: pruebasIniciales }));
  const [nuevos, setNuevos] = useState(0);
  const [revisionMapa, setRevisionMapa] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [activo, setActivo] = useState(true);
  const enVuelo = useRef<AbortController | null>(null);
  const ultimaActualizacionMapa = useRef(0);
  const datosActuales = useRef<Snapshot>(normalizar({ eventosTracking: eventosIniciales, pruebasDesvio: pruebasIniciales }));
  const esGeografico = ["DESVIO_RUTA", "DESVIO_DE_RUTA", "VIAJE_SIN_GUIA", "SIN_GUIA"].includes(tipo);
  const finalizado = estadoCaso === "CONFIRMADA" || estadoCaso === "DESESTIMADA";

  useEffect(() => {
    if (finalizado || !activo) return;
    if (ultimaActualizacionMapa.current === 0) {
      ultimaActualizacionMapa.current = Date.now();
    }
    let montado = true;
    const consultar = async () => {
      if (document.hidden || enVuelo.current) return;
      const controlador = new AbortController();
      enVuelo.current = controlador;
      try {
        const res = await fetch(`${BASE_PATH}/casos/${id}/evidencia-viva`, {
          cache: "no-store", signal: controlador.signal,
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const respuesta = await res.json() as Snapshot;
        if (!Array.isArray(respuesta.eventosTracking) || !Array.isArray(respuesta.pruebasDesvio)) {
          throw new Error("Respuesta inesperada");
        }
        if (!montado || controlador.signal.aborted) return;
        const siguiente = normalizar(respuesta);
        const anterior = datosActuales.current;
        if (hashSnapshot(anterior) !== hashSnapshot(siguiente)) {
          const cantidadNueva = Math.max(0, siguiente.eventosTracking.length - anterior.eventosTracking.length) +
            Math.max(0, siguiente.pruebasDesvio.length - anterior.pruebasDesvio.length);
          datosActuales.current = siguiente;
          setDatos(siguiente);
          if (cantidadNueva > 0) setNuevos(n => n + cantidadNueva);
          // La trayectoria se consulta menos frecuentemente que los eventos y solo si llegó información.
          if (esGeografico && cantidadNueva > 0 && Date.now() - ultimaActualizacionMapa.current >= MAPA_INTERVALO_MS) {
            ultimaActualizacionMapa.current = Date.now();
            setRevisionMapa(n => n + 1);
          }
        }
        setError(null);
      } catch (e) {
        if (montado && !controlador.signal.aborted) setError((e as Error).message);
      } finally {
        if (enVuelo.current === controlador) enVuelo.current = null;
      }
    };
    const intervalo = window.setInterval(() => { void consultar(); }, INTERVALO_MS);
    const mostrar = () => { if (!document.hidden) void consultar(); };
    document.addEventListener("visibilitychange", mostrar);
    void consultar();
    return () => {
      montado = false;
      window.clearInterval(intervalo);
      document.removeEventListener("visibilitychange", mostrar);
      enVuelo.current?.abort();
      enVuelo.current = null;
    };
  }, [id, activo, finalizado, esGeografico]);

  return <>
    <section className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-zinc-600 dark:text-zinc-400">
        <span role="status" aria-live="polite">
          {finalizado ? "Caso resuelto: eventos históricos" : activo ? "Eventos en vivo · actualización automática cada 5 s" : "Actualización automática pausada"}
          {nuevos > 0 ? ` · ${nuevos} registros nuevos desde que abriste esta pantalla` : ""}
        </span>
        {!finalizado && <button type="button" onClick={() => setActivo(v => !v)}
          className="rounded-lg border border-zinc-300 px-3 py-1.5 hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-900">
          {activo ? "Pausar actualización" : "Reanudar actualización"}
        </button>}
      </div>
      {error && <p role="alert" className="rounded-lg bg-amber-50 p-3 text-xs text-amber-800 dark:bg-amber-950 dark:text-amber-200">
        No se pudieron consultar novedades ({error}). Se conservan los registros cargados y se reintentará automáticamente.
      </p>}
      {tipo !== "DESVIO_RUTA" && tipo !== "DESVIO_DE_RUTA" && <EventosTrackingPaginados eventos={datos.eventosTracking} />}
    </section>
    {esGeografico && <GeografiaCaso id={id} tipo={tipo} guiaId={guiaId} fechaDeteccion={fechaDeteccion}
      pruebas={datos.pruebasDesvio} circulacion={circulacion} revisionDatos={revisionMapa}
      actualizacionActiva={activo && !finalizado} />}
  </>;
}

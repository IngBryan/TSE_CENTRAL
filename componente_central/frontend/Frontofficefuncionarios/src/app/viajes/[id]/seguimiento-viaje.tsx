"use client";

import dynamic from "next/dynamic";
import { useEffect, useRef, useState } from "react";
import { BASE_PATH } from "@/lib/base-path";
import { habilitaPollingGps } from "@/lib/polling-gps";
import {
  unirPosiciones, type PaginaGps, type PuntoGpsSeguimiento, type UltimaGps,
} from "@/lib/seguimiento";
import type { EstadoViaje } from "@/lib/viajes";

const MapaSeguimiento = dynamic(() => import("./mapa-seguimiento"), { ssr: false });
const SIETE_DIAS = 7 * 24 * 60 * 60 * 1000;
const MAX_PUNTOS = 10000;
const SOLAPAMIENTO = 2 * 60 * 1000; // Releer últimos 2 min para GPS retrasados.

const tiempo = (valor: string) => new Date(valor).toLocaleString("es-UY");

async function consultar<T>(id: string, recurso: "gps" | "ultima", params: URLSearchParams | null, signal: AbortSignal): Promise<T> {
  const url = `${BASE_PATH}/viajes/${encodeURIComponent(id)}/seguimiento/${recurso}${params ? `?${params}` : ""}`;
  const res = await fetch(url, { signal, cache: "no-store" });
  if (!res.ok) throw new Error(res.status === 503 ? "El servicio GPS no está disponible." : `No se pudo consultar ${recurso} (${res.status}).`);
  return (await res.json()) as T;
}

/** Pagina candidatos por ventanas de 7 días; usa el cursor de Mongo, no el último GPS mostrado. */
async function obtenerHistorial(id: string, desde: number, hasta: number, signal: AbortSignal) {
  const puntos: PuntoGpsSeguimiento[] = [];
  let incompleto = false;
  for (let inicio = desde; inicio < hasta; inicio += SIETE_DIAS) {
    const fin = Math.min(hasta, inicio + SIETE_DIAS);
    let cursor = inicio;
    while (cursor < fin) {
      const params = new URLSearchParams({
        desde: new Date(cursor).toISOString(), hasta: new Date(fin).toISOString(), limite: "500",
      });
      const pagina = await consultar<PaginaGps>(id, "gps", params, signal);
      puntos.push(...pagina.posiciones);
      if (puntos.length >= MAX_PUNTOS) {
        incompleto = true;
        return { puntos: unirPosiciones([], puntos.slice(0, MAX_PUNTOS)), incompleto };
      }
      if (!pagina.hayMas) break;
      const siguiente = pagina.siguienteDesde ? Date.parse(pagina.siguienteDesde) : NaN;
      if (!Number.isFinite(siguiente) || siguiente <= cursor) {
        throw new Error("El cursor GPS no avanzó; se interrumpió la paginación para evitar un ciclo.");
      }
      cursor = siguiente;
    }
  }
  return { puntos: unirPosiciones([], puntos), incompleto };
}

export function SeguimientoViaje({
  id, estado, asignacion, inicio, fin,
}: {
  id: string;
  estado: EstadoViaje;
  asignacion: string | null;
  inicio: string | null;
  fin: string | null;
}) {
  const [posiciones, setPosiciones] = useState<PuntoGpsSeguimiento[]>([]);
  const [ultima, setUltima] = useState<PuntoGpsSeguimiento | null>(null);
  const [cargando, setCargando] = useState(true);
  const [errorGps, setErrorGps] = useState<string | null>(null);
  const [incompleto, setIncompleto] = useState(false);
  const [mostrarRecorrido, setMostrarRecorrido] = useState(true);
  const [centrar, setCentrar] = useState(0);
  const [centrarRecorrido, setCentrarRecorrido] = useState(0);
  const ultimaFecha = useRef<number | null>(null);
  const desdeMs = Date.parse(inicio ?? asignacion ?? "");
  const actualizacionAutomatica = habilitaPollingGps(estado, desdeMs);

  useEffect(() => {
    const controlador = new AbortController();
    const cargar = async () => {
      try {
        const { puntos, incompleto: parcial } = Number.isFinite(desdeMs)
          ? await obtenerHistorial(id, desdeMs, Math.max(desdeMs + 1, Date.parse(fin ?? "") || Date.now() + 1000), controlador.signal)
          : { puntos: [] as PuntoGpsSeguimiento[], incompleto: false };
        if (controlador.signal.aborted) return;
        setPosiciones(puntos);
        ultimaFecha.current = puntos.length ? Date.parse(puntos[puntos.length - 1].fechaHora) : null;
        setIncompleto(parcial);
        setErrorGps(null);
      } catch (e) {
        if (!controlador.signal.aborted) setErrorGps((e as Error).message);
      } finally {
        if (!controlador.signal.aborted) setCargando(false);
      }
    };
    void cargar();
    return () => controlador.abort();
  }, [id, desdeMs, fin]);

  useEffect(() => {
    if (!actualizacionAutomatica || cargando) return;
    const controller = new AbortController();
    let ocupado = false;
    const refrescar = async () => {
      if (ocupado) return;
      ocupado = true;
      try {
        const hasta = Date.now() + 1000;
        const desde = Math.max(desdeMs, (ultimaFecha.current ?? desdeMs) - SOLAPAMIENTO);
        const [nuevo, ultimaActual] = await Promise.all([
          obtenerHistorial(id, desde, hasta, controller.signal),
          consultar<UltimaGps>(id, "ultima", null, controller.signal),
        ]);
        if (controller.signal.aborted) return;
        setPosiciones((anterior) => {
          const unido = unirPosiciones(anterior, nuevo.puntos);
          const truncado = unido.length > MAX_PUNTOS ? unido.slice(-MAX_PUNTOS) : unido;
          if (truncado.length) ultimaFecha.current = Date.parse(truncado[truncado.length - 1].fechaHora);
          return truncado;
        });
        setUltima(ultimaActual.posicion);
        if (nuevo.incompleto) setIncompleto(true);
        setErrorGps(null);
      } catch (e) {
        if (!controller.signal.aborted) setErrorGps((e as Error).message);
      } finally { ocupado = false; }
    };
    void refrescar();
    const timer = setInterval(() => void refrescar(), 15000);
    return () => { clearInterval(timer); controller.abort(); };
  }, [id, actualizacionAutomatica, cargando, desdeMs]);

  const ultimaMostrada = ultima ?? (posiciones.length ? posiciones[posiciones.length - 1] : null);

  return (
    <section className="flex flex-col gap-3">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h2 className="text-lg font-semibold text-black dark:text-zinc-50">Recorrido del vehículo</h2>
        <span className="text-xs text-zinc-500">
          {actualizacionAutomatica ? "Actualización automática cada 15 s" : "Recorrido histórico"}
        </span>
      </div>
      <div className="rounded-2xl bg-white p-5 text-sm shadow-sm dark:bg-zinc-950">
        <div className="mb-3 flex flex-wrap items-center gap-4">
          <label className="flex cursor-pointer items-center gap-2">
            <input type="checkbox" checked={mostrarRecorrido} onChange={(e) => setMostrarRecorrido(e.target.checked)} />
            <span>Recorrido GPS ({posiciones.length} posiciones)</span>
          </label>
          <button type="button" className="rounded-lg border px-3 py-1 text-xs hover:bg-zinc-100 dark:hover:bg-zinc-800 disabled:opacity-50"
            disabled={!ultimaMostrada} onClick={() => setCentrar((x) => x + 1)}>Centrar última posición</button>
          <button type="button" className="rounded-lg border px-3 py-1 text-xs hover:bg-zinc-100 dark:hover:bg-zinc-800 disabled:opacity-50"
            disabled={!posiciones.length} onClick={() => setCentrarRecorrido(x => x + 1)}>
            Ver recorrido completo
          </button>
        </div>
        <MapaSeguimiento posiciones={posiciones} ultima={ultimaMostrada}
          mostrarRecorrido={mostrarRecorrido} centrar={centrar}
          centrarRecorrido={centrarRecorrido} />
        <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-zinc-600 dark:text-zinc-400">
          <span><span className="font-semibold text-orange-600">┄┄</span> Recorrido del vehículo</span>
        </div>
        {cargando && <p className="mt-3 text-zinc-500">Cargando trayectoria GPS…</p>}
        {!cargando && posiciones.length === 0 && !errorGps && <p className="mt-1 text-zinc-500">No hay posiciones de Tracking asociadas inequívocamente a esta Guía.</p>}
        {posiciones.length > 0 && <p className="mt-2 text-xs text-zinc-500">Primera posición registrada: {tiempo(posiciones[0].fechaHora)}.</p>}
        {ultimaMostrada && <p className="mt-2 text-xs text-zinc-500">Última posición registrada: {tiempo(ultimaMostrada.fechaHora)}. No confirma que el GPS siga conectado.</p>}
        {incompleto && <p className="mt-2 text-amber-800 dark:text-amber-300">El recorrido supera el límite de {MAX_PUNTOS.toLocaleString("es-UY")} posiciones cargadas. La vista es parcial.</p>}
        {errorGps && <p role="alert" className="mt-2 text-red-700 dark:text-red-300">GPS: {errorGps}</p>}
      </div>
    </section>
  );
}

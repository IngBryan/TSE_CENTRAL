"use client";

import dynamic from "next/dynamic";
import Link from "next/link";
import { useEffect, useState } from "react";
import { BASE_PATH } from "@/lib/base-path";
import { fechaHora, type CirculacionVista, type PaginaGpsCaso, type PruebaDesvioVista } from "@/lib/casos";
import { unirPosiciones, type PuntoGpsSeguimiento, type RutaAutorizada } from "@/lib/seguimiento";

const MapaEvidencia = dynamic(() => import("./mapa-evidencia"), { ssr: false });
const MAX_PUNTOS = 10000;
const TAMANIO = 250;

async function consultar<T>(id: number, recurso: "gps" | "rutas", parametros: URLSearchParams | null,
  signal: AbortSignal): Promise<T> {
  const url = `${BASE_PATH}/casos/${id}/geografia/${recurso}${parametros ? `?${parametros}` : ""}`;
  const res = await fetch(url, { cache: "no-store", signal });
  if (!res.ok) throw new Error(`No se pudo recuperar ${recurso} (${res.status}).`);
  return await res.json() as T;
}

/** La trayectoria se construye solo con GPS que figuran como evidencia del expediente. */
async function gpsDelCaso(id: number, signal: AbortSignal) {
  const puntos: PuntoGpsSeguimiento[] = [];
  let faltantes = 0;
  let cursor = 0;
  let incompleto = false;
  let total = 0;
  for (;;) {
    const datos = await consultar<PaginaGpsCaso>(id, "gps", new URLSearchParams({
      indice: String(cursor), limite: String(TAMANIO),
    }), signal);
    puntos.push(...datos.posiciones);
    faltantes += datos.sinRecuperar;
    total = datos.totalClaves;
    if (datos.siguienteIndice === null) break;
    if (datos.siguienteIndice <= cursor) throw new Error("La paginación GPS no avanzó.");
    cursor = datos.siguienteIndice;
    if (cursor >= MAX_PUNTOS) { incompleto = true; break; }
  }
  return { puntos: unirPosiciones([], puntos), faltantes, incompleto, total };
}

export function GeografiaCaso({ id, tipo, guiaId, pruebas, circulacion }: {
  id: number;
  tipo: string;
  guiaId: string | null;
  pruebas: PruebaDesvioVista[];
  circulacion: CirculacionVista | null;
}) {
  const esDesvio = tipo === "DESVIO_RUTA" || tipo === "DESVIO_DE_RUTA";
  const esSinGuia = tipo === "VIAJE_SIN_GUIA" || tipo === "SIN_GUIA";
  const [rutas, setRutas] = useState<RutaAutorizada[]>([]);
  const [puntos, setPuntos] = useState<PuntoGpsSeguimiento[]>([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [recarga, setRecarga] = useState(0);

  useEffect(() => {
    const ctrl = new AbortController();
    const cargar = async () => {
      setCargando(true); setError(null); setAviso(null);
      try {
        if (esDesvio) {
          const rutasLeidas = await consultar<RutaAutorizada[]>(id, "rutas", null, ctrl.signal);
          if (!ctrl.signal.aborted) setRutas(rutasLeidas);
        } else if (esSinGuia) {
          const gps = await gpsDelCaso(id, ctrl.signal);
          if (ctrl.signal.aborted) return;
          setPuntos(gps.puntos);
          if (gps.incompleto || gps.faltantes) {
            setAviso(`${gps.puntos.length} de ${gps.total} referencias GPS recuperadas. ` +
              (gps.incompleto ? "Se alcanzó el límite de 10.000 evidencias. " : "") +
              (gps.faltantes ? `${gps.faltantes} referencias no se encontraron en MongoDB.` : ""));
          }
        }
      } catch (e) {
        if (!ctrl.signal.aborted) setError((e as Error).message);
      } finally { if (!ctrl.signal.aborted) setCargando(false); }
    };
    void cargar();
    return () => ctrl.abort();
  }, [id, esDesvio, esSinGuia, recarga]);

  if (!esDesvio && !esSinGuia) return null;
  const mapaVisible = (esDesvio && (pruebas.length > 0 || rutas.length > 0)) || (esSinGuia && puntos.length > 0);
  return <section className="flex flex-col gap-3">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <h2 className="text-lg font-semibold text-black dark:text-zinc-50">
        {esDesvio ? "Evidencia geométrica del desvío" : "Circulación detectada sin Guía"}
      </h2>
      <button type="button" onClick={() => setRecarga(x => x + 1)}
        className="rounded-lg border border-zinc-300 px-3 py-1.5 text-xs hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-900">
        Actualizar mapa
      </button>
    </div>
    <div className="rounded-2xl bg-white p-5 text-sm shadow-sm dark:bg-zinc-950">
      {esSinGuia && (circulacion ? <div className="mb-4 grid gap-2 sm:grid-cols-2">
        <p><b>Estado de circulación:</b> {circulacion.estado.replaceAll("_", " ")}</p>
        <p><b>Matrícula:</b> {circulacion.matricula}</p>
        <p><b>Inicio observado:</b> {fechaHora(circulacion.inicio)}</p>
        <p><b>Fin:</b> {circulacion.fin ? fechaHora(circulacion.fin) : "En seguimiento"}</p>
        <p><b>Motivo de cierre:</b> {circulacion.motivoFin?.replaceAll("_", " ") ?? "-"}</p>
        <p><b>Evidencias GPS vinculadas:</b> {circulacion.cantidadClavesGps}</p>
        {circulacion.finalInferido && <p className="sm:col-span-2 text-amber-700 dark:text-amber-300">
          Final inferido: no equivale a un fin de viaje formalmente confirmado.
        </p>}
      </div> : <p className="mb-3 text-amber-700 dark:text-amber-300">
        Este expediente no tiene circulación persistida consultable.
      </p>)}
      {esDesvio && <div className="mb-4 space-y-3">
        {pruebas.length === 0 && <p>No hay pruebas geométricas persistidas para este expediente.</p>}
        {pruebas.map((p, i) => <div key={`${p.fechaHora}-${i}`} className="rounded-lg bg-zinc-50 p-3 dark:bg-zinc-900">
          <p className="font-medium">Confirmación {i + 1}: {fechaHora(p.fechaHora)}</p>
          <div className="mt-1 grid gap-1 sm:grid-cols-2">
            <p>Primera medición: {fechaHora(p.fechaPareja)} · {p.distanciaParejaMetros.toFixed(0)} m</p>
            <p>Medición confirmante: {p.distanciaConfirmanteMetros.toFixed(0)} m</p>
            <p>Umbral aplicado: {p.umbralMetros.toFixed(0)} m</p>
            <p>Ventana máxima: {p.ventanaMaximaSegundos} s</p>
          </div>
          <details className="mt-2 text-xs"><summary className="cursor-pointer">Hashes de rutas evaluadas</summary>
            <ul className="mt-1 break-all font-mono">{p.hashesRutas.map(h => <li key={h}>{h}</li>)}</ul>
          </details>
        </div>)}
        <p className="text-xs text-zinc-500">Los puntos de confirmación están almacenados en el expediente. No se reconstruye una trayectoria completa entre ellos.</p>
      </div>}
      {cargando && <p role="status" className="py-4 text-zinc-500">Cargando información geográfica…</p>}
      {error && <p role="alert" className="mb-3 rounded-lg bg-amber-50 p-3 text-amber-800 dark:bg-amber-950 dark:text-amber-300">{error}</p>}
      {aviso && <p role="status" className="mb-3 rounded-lg bg-amber-50 p-3 text-amber-800 dark:bg-amber-950 dark:text-amber-300">Historial parcial: {aviso}</p>}
      {!cargando && mapaVisible ? <>
        <MapaEvidencia rutas={rutas} puntos={puntos} pruebas={pruebas} />
        <p className="mt-2 text-xs text-zinc-500">
          {esDesvio ? `Rutas autorizadas: ${rutas.length}. Naranja: primer GPS fuera de ruta; rojo: confirmación.`
            : `GPS del expediente: ${puntos.length}. Verde: primera posición; rojo: última posición observada.`}
        </p>
      </> : !cargando && !error && <p className="py-4 text-zinc-500">No hay coordenadas disponibles para mostrar.</p>}
      {guiaId && esDesvio && <Link className="mt-3 inline-block text-sm text-sky-700 underline dark:text-sky-300"
        href={`/viajes/${encodeURIComponent(guiaId)}`}>Ver seguimiento completo del viaje</Link>}
    </div>
  </section>;
}

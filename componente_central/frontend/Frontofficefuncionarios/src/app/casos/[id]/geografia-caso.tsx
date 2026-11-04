"use client";

import dynamic from "next/dynamic";
import Link from "next/link";
import { useEffect, useState } from "react";
import { BASE_PATH } from "@/lib/base-path";
import { fechaHora, type CirculacionVista, type PaginaGpsCaso, type PruebaDesvioVista } from "@/lib/casos";
import { unirPosiciones, type PaginaGps, type PuntoGpsSeguimiento, type RutaAutorizada } from "@/lib/seguimiento";

const MapaEvidencia = dynamic(() => import("./mapa-evidencia"), { ssr: false });
const MAX_PUNTOS = 10000;
const TAMANIO = 250;
const SIETE_DIAS = 7 * 24 * 60 * 60 * 1000;
const CONFIRMACIONES_POR_PAGINA = 10;
const metros = (n: number) => `${n.toLocaleString("es-UY", { maximumFractionDigits: 1 })} m`;

async function consultar<T>(id: number, recurso: "gps" | "rutas" | "ventana", parametros: URLSearchParams | null,
  signal: AbortSignal): Promise<T> {
  const url = `${BASE_PATH}/casos/${id}/geografia/${recurso}${parametros ? `?${parametros}` : ""}`;
  const res = await fetch(url, { cache: "no-store", signal });
  if (!res.ok) throw new Error(`No se pudo recuperar ${recurso} (${res.status}).`);
  return await res.json() as T;
}

type VentanaViaje = { idGuia: string; desde: string | null; hasta: string | null };

/** Reutiliza el BFF autenticado de seguimiento. Pagina por fecha real de GPS, no por confirmaciones.
 * Así el recorrido incluye las posiciones anteriores y posteriores al desvío. */
async function gpsDelViaje(idGuia: string, desdeTexto: string, hastaTexto: string | null, signal: AbortSignal) {
  const desde = Date.parse(desdeTexto);
  const hasta = hastaTexto ? Date.parse(hastaTexto) + 1000 : Date.now() + 1000;
  if (!Number.isFinite(desde) || !Number.isFinite(hasta) || desde >= hasta) {
    throw new Error("El período del viaje no permite consultar posiciones GPS.");
  }
  const puntos: PuntoGpsSeguimiento[] = [];
  for (let inicio = desde; inicio < hasta; inicio += SIETE_DIAS) {
    const fin = Math.min(hasta, inicio + SIETE_DIAS);
    let cursor = inicio;
    while (cursor < fin) {
      const params = new URLSearchParams({
        desde: new Date(cursor).toISOString(), hasta: new Date(fin).toISOString(), limite: "500",
      });
      const url = `${BASE_PATH}/viajes/${encodeURIComponent(idGuia)}/seguimiento/gps?${params}`;
      const res = await fetch(url, { cache: "no-store", signal });
      if (!res.ok) throw new Error(`No se pudo recuperar el recorrido GPS (${res.status}).`);
      const pagina = await res.json() as PaginaGps;
      puntos.push(...pagina.posiciones);
      if (puntos.length >= MAX_PUNTOS) {
        return { puntos: unirPosiciones([], puntos.slice(0, MAX_PUNTOS)), incompleto: true };
      }
      if (!pagina.hayMas) break;
      const siguiente = pagina.siguienteDesde ? Date.parse(pagina.siguienteDesde) : NaN;
      if (!Number.isFinite(siguiente) || siguiente <= cursor) {
        throw new Error("La paginación del historial GPS no avanzó.");
      }
      cursor = siguiente;
    }
  }
  return { puntos: unirPosiciones([], puntos), incompleto: false };
}

/** Sin guía: solamente existen referencias GPS asociadas al expediente. */
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

export function GeografiaCaso({ id, tipo, guiaId, pruebas, circulacion, revisionDatos = 0 }: {
  id: number;
  tipo: string;
  guiaId: string | null;
  pruebas: PruebaDesvioVista[];
  circulacion: CirculacionVista | null;
  revisionDatos?: number;
}) {
  const esDesvio = tipo === "DESVIO_RUTA" || tipo === "DESVIO_DE_RUTA";
  const esSinGuia = tipo === "VIAJE_SIN_GUIA" || tipo === "SIN_GUIA";
  const [rutas, setRutas] = useState<RutaAutorizada[]>([]);
  const [puntos, setPuntos] = useState<PuntoGpsSeguimiento[]>([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [recarga, setRecarga] = useState(0);
  const [pagina, setPagina] = useState(1);
  const [claveSeleccionada, setClaveSeleccionada] = useState<string | null>(null);
  const [revisionFoco, setRevisionFoco] = useState(0);
  const [modoEncuadre, setModoEncuadre] = useState<"todo" | "confirmacion">("todo");
  const [mostrarRutas, setMostrarRutas] = useState(true);
  const [mostrarRecorrido, setMostrarRecorrido] = useState(true);
  const totalPaginas = Math.max(1, Math.ceil(pruebas.length / CONFIRMACIONES_POR_PAGINA));
  const paginaActual = Math.min(pagina, totalPaginas);
  // Guardar identidad, no índice: un nuevo registro nunca cambia la prueba elegida.
  const clavePrueba = (p: PruebaDesvioVista) => `${p.fechaPareja}|${p.fechaHora}|${p.latConfirmante}|${p.lonConfirmante}`;
  const indiceBuscado = claveSeleccionada === null ? 0 : pruebas.findIndex(p => clavePrueba(p) === claveSeleccionada);
  const indiceActual = indiceBuscado < 0 ? 0 : indiceBuscado;
  const pruebaActual = pruebas[indiceActual];
  const paginaInicio = (paginaActual - 1) * CONFIRMACIONES_POR_PAGINA;
  const mayorSeparacion = pruebas.length ? Math.max(...pruebas.map(p => p.distanciaConfirmanteMetros)) : null;
  const seleccionar = (indice: number) => {
    setClaveSeleccionada(clavePrueba(pruebas[indice]));
    setModoEncuadre("confirmacion");
    setRevisionFoco(n => n + 1);
  };
  const verTodo = () => { setModoEncuadre("todo"); setRevisionFoco(n => n + 1); };
  const cambiarPagina = (destino: number) => {
    setPagina(Math.max(1, Math.min(destino, totalPaginas)));
  };

  useEffect(() => {
    const ctrl = new AbortController();
    const cargar = async () => {
      setCargando(true); setError(null); setAviso(null);
      // Conservar la geometría anterior mientras se consultan nuevas posiciones.
      try {
        if (esDesvio) {
          // La consulta del GPS es opcional para el expediente: si falla, se conserva
          // la evidencia persistida y se informa que la trayectoria está incompleta.
          const [rutasResultado, ventanaResultado] = await Promise.allSettled([
            consultar<RutaAutorizada[]>(id, "rutas", null, ctrl.signal),
            consultar<VentanaViaje>(id, "ventana", null, ctrl.signal),
          ]);
          if (ctrl.signal.aborted) return;
          if (rutasResultado.status === "fulfilled") setRutas(rutasResultado.value);
          else setError(rutasResultado.reason instanceof Error ? rutasResultado.reason.message : "No se pudieron cargar las rutas.");
          if (ventanaResultado.status === "fulfilled" && ventanaResultado.value.desde && guiaId) {
            try {
              if (ventanaResultado.value.idGuia !== guiaId) throw new Error("La Guía del expediente no coincide con el viaje.");
              const gps = await gpsDelViaje(guiaId, ventanaResultado.value.desde, ventanaResultado.value.hasta, ctrl.signal);
              if (ctrl.signal.aborted) return;
              setPuntos(gps.puntos);
              if (gps.incompleto) setAviso("Se alcanzó el límite de 10.000 posiciones; el recorrido mostrado es parcial.");
            } catch (e) {
              if (!ctrl.signal.aborted) setAviso(`No se pudo cargar la trayectoria completa: ${(e as Error).message}`);
            }
          } else {
            setAviso(ventanaResultado.status === "rejected"
              ? "No se pudo obtener el período del viaje; el mapa muestra las rutas y las pruebas del caso."
              : "No hay fechas de asignación o inicio válidas para reconstruir el viaje.");
          }
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
  }, [id, guiaId, esDesvio, esSinGuia, recarga, revisionDatos]);

  if (!esDesvio && !esSinGuia) return null;
  const mapaVisible = (esDesvio && (pruebas.length > 0 || rutas.length > 0 || puntos.length > 0)) || (esSinGuia && puntos.length > 0);
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
        <p className="text-zinc-600 dark:text-zinc-400">
          El mapa compara las rutas autorizadas con el recorrido GPS efectivamente registrado. Elegí una confirmación
          para localizar las dos mediciones que demuestran el desvío sin ocultar el resto del trayecto.
        </p>
        <div className="grid gap-3 rounded-xl bg-zinc-50 p-3 sm:grid-cols-3 dark:bg-zinc-900">
          <p><span className="block text-xs text-zinc-500">Confirmaciones registradas</span>
            <b>{pruebas.length.toLocaleString("es-UY")}</b></p>
          <p><span className="block text-xs text-zinc-500">Mayor separación a la ruta</span>
            <b>{mayorSeparacion === null ? "-" : metros(mayorSeparacion)}</b></p>
          <p><span className="block text-xs text-zinc-500">Rutas autorizadas</span>
            <b>{rutas.length}</b></p>
        </div>
        {pruebaActual ? <div className="rounded-xl border border-orange-200 p-3 dark:border-orange-900" aria-live="polite">
          <p className="font-medium">Confirmación seleccionada {indiceActual + 1} de {pruebas.length}</p>
          <p className="mt-1 text-xs text-zinc-500">{fechaHora(pruebaActual.fechaHora)}</p>
          <div className="mt-2 grid gap-2 sm:grid-cols-2">
            <p><span className="block text-zinc-500">Primera posición (amarilla)</span>
              <b>{metros(pruebaActual.distanciaParejaMetros)}</b> de la ruta autorizada</p>
            <p><span className="block text-zinc-500">Segunda posición (roja)</span>
              <b>{metros(pruebaActual.distanciaConfirmanteMetros)}</b> de la ruta autorizada</p>
          </div>
          <p className="mt-2 text-xs text-zinc-600 dark:text-zinc-400">
            Límite permitido: {metros(pruebaActual.umbralMetros)}.
            Tiempo entre mediciones: {Math.round((Date.parse(pruebaActual.fechaHora) - Date.parse(pruebaActual.fechaPareja)) / 1000)} s
            (máximo {pruebaActual.ventanaMaximaSegundos} s).
          </p>
        </div> : <p>No hay pruebas geométricas persistidas para este expediente.</p>}
      </div>}
      {cargando && <p role="status" className="py-4 text-zinc-500">Cargando información geográfica…</p>}
      {error && <p role="alert" className="mb-3 rounded-lg bg-amber-50 p-3 text-amber-800 dark:bg-amber-950 dark:text-amber-300">{error}</p>}
      {aviso && <p role="status" className="mb-3 rounded-lg bg-amber-50 p-3 text-amber-800 dark:bg-amber-950 dark:text-amber-300">Historial parcial: {aviso}</p>}
      {mapaVisible ? <>
        {esDesvio && <div className="mb-3 flex flex-wrap items-center gap-x-4 gap-y-2">
          <label className="flex cursor-pointer items-center gap-2">
            <input type="checkbox" checked={mostrarRutas} onChange={e => setMostrarRutas(e.target.checked)} />
            <span>Rutas autorizadas ({rutas.length})</span>
          </label>
          <label className="flex cursor-pointer items-center gap-2">
            <input type="checkbox" checked={mostrarRecorrido} onChange={e => setMostrarRecorrido(e.target.checked)} />
            <span>Recorrido GPS ({puntos.length} posiciones)</span>
          </label>
          <button type="button" onClick={verTodo}
            className="rounded-lg border px-3 py-1.5 text-xs hover:bg-zinc-100 dark:hover:bg-zinc-900">
            Mostrar todo en el mapa
          </button>
          {guiaId && <Link className="text-xs text-sky-700 underline dark:text-sky-300"
            href={`/viajes/${encodeURIComponent(guiaId)}`}>Ver recorrido operativo del viaje</Link>}
        </div>}
        <MapaEvidencia rutas={rutas} puntos={puntos} pruebas={esDesvio && pruebaActual ? [pruebaActual] : []}
          mostrarRutas={!esDesvio || mostrarRutas} mostrarRecorrido={!esDesvio || mostrarRecorrido}
          enfocar={revisionFoco} modoEncuadre={modoEncuadre} />
        <p className="mt-2 text-xs text-zinc-500">
          {esDesvio ? `Verde/azul/violeta: rutas autorizadas. Naranja discontinuo: trayectoria GPS realizada. Amarillo: primera medición; rojo: segunda medición confirmante. La selección no oculta el recorrido.`
            : `GPS del expediente: ${puntos.length}. Verde: primera posición; rojo: última posición observada.`}
        </p>
        {esDesvio && puntos.length === 0 && !aviso && <p className="mt-2 text-xs text-zinc-500">
          No se recuperaron posiciones del seguimiento histórico. Las confirmaciones del expediente siguen disponibles.
        </p>}
      </> : !cargando && !error && <p className="py-4 text-zinc-500">No hay coordenadas disponibles para mostrar.</p>}
      {esDesvio && pruebas.length > 0 && <div className="mt-5">
        <h3 className="mb-2 font-medium">Historial de confirmaciones ({pruebas.length})</h3>
        <p className="mb-2 text-xs text-zinc-500">Seleccioná una fila para localizar sus dos posiciones en el mapa.</p>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs sm:text-sm">
            <thead className="border-b border-black/10 text-zinc-500 dark:border-white/10">
              <tr><th className="py-2 pr-2 font-medium">N.º</th>
                <th className="py-2 pr-2 font-medium">Hora</th>
                <th className="py-2 pr-2 text-right font-medium">Separación</th>
                <th className="py-2 text-right font-medium">Mapa</th></tr>
            </thead>
            <tbody>{pruebas.slice(paginaInicio, paginaInicio + CONFIRMACIONES_POR_PAGINA).map((prueba, i) => {
              const indice = paginaInicio + i;
              return <tr key={`${indice}-${prueba.fechaHora}`} className={`border-b border-black/5 dark:border-white/10 ${indice === indiceActual ? "bg-orange-50 dark:bg-orange-950/30" : ""}`}>
                <td className="py-2 pr-2">{indice + 1}</td>
                <td className="py-2 pr-2 tabular-nums">{fechaHora(prueba.fechaHora)}</td>
                <td className="py-2 pr-2 text-right tabular-nums">{metros(prueba.distanciaConfirmanteMetros)}</td>
                <td className="py-2 text-right"><button type="button" aria-pressed={indice === indiceActual}
                  onClick={() => seleccionar(indice)}
                  className="rounded-lg border px-2 py-1 hover:bg-orange-100 dark:hover:bg-orange-900">
                  {indice === indiceActual ? "Seleccionada" : "Ver puntos"}
                </button></td>
              </tr>;
            })}</tbody>
          </table>
        </div>
        <nav className="mt-3 flex flex-wrap items-center justify-between gap-2" aria-label="Paginación de confirmaciones geométricas">
          <span className="text-xs text-zinc-500">Página {paginaActual} de {totalPaginas}</span>
          <div className="flex gap-2">
            <button type="button" disabled={paginaActual === 1} onClick={() => cambiarPagina(paginaActual - 1)}
              className="rounded-lg border px-3 py-1.5 text-xs disabled:opacity-40">Anterior</button>
            <button type="button" disabled={paginaActual === totalPaginas} onClick={() => cambiarPagina(paginaActual + 1)}
              className="rounded-lg border px-3 py-1.5 text-xs disabled:opacity-40">Siguiente</button>
          </div>
        </nav>
      </div>}
    </div>
  </section>;
}

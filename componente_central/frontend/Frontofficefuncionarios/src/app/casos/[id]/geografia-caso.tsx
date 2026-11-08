"use client";

import dynamic from "next/dynamic";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { BASE_PATH } from "@/lib/base-path";
import { primeraConfirmacionGps, referenciasDePrueba, soloPosteriores } from "@/lib/evidencia-desvio";
import { fechaHora, type CirculacionVista, type PaginaGpsCaso, type PruebaDesvioVista } from "@/lib/casos";
import { unirPosiciones, type PaginaGps, type PuntoGpsSeguimiento, type RutaAutorizada } from "@/lib/seguimiento";

const MapaEvidencia = dynamic(() => import("./mapa-evidencia"), { ssr: false });
const MAX_PUNTOS = 10000;
const SOLAPAMIENTO_MS = 2 * 60 * 1000; // Recuperar puntos retrasados del ultimo par de minutos.
const TAMANIO = 250;
const SIETE_DIAS = 7 * 24 * 60 * 60 * 1000;
const CONFIRMACIONES_POR_PAGINA = 10;
const metros = (n: number) => `${n.toLocaleString("es-UY", { maximumFractionDigits: 1 })} m`;

async function consultar<T>(id: number, recurso: "gps" | "rutas" | "rutas-evidencia" | "ventana", parametros: URLSearchParams | null,
  signal: AbortSignal): Promise<T> {
  const url = `${BASE_PATH}/casos/${id}/geografia/${recurso}${parametros ? `?${parametros}` : ""}`;
  const res = await fetch(url, { cache: "no-store", signal });
  if (!res.ok) throw new Error(`No se pudo recuperar ${recurso} (${res.status}).`);
  return await res.json() as T;
}

type VentanaViaje = { idGuia: string; desde: string | null; hasta: string | null };

/** Reutiliza el BFF autenticado de seguimiento para la trayectoria operativa completa.
 * Las mediciones posteriores se clasifican localmente por la primera confirmación GPS,
 * sin mezclar el historial completo con las dos coordenadas probatorias archivadas. */
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
      const siguiente = pagina.siguienteDesde ? Date.parse(pagina.siguienteDesde) : Number.NaN;
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
    // WildFly omite las propiedades nulas al terminar la paginación.
    if (datos.siguienteIndice == null) break;
    if (datos.siguienteIndice <= cursor) throw new Error("La paginación GPS no avanzó.");
    cursor = datos.siguienteIndice;
    if (cursor >= MAX_PUNTOS) { incompleto = true; break; }
  }
  return { puntos: unirPosiciones([], puntos), faltantes, incompleto, total };
}

export function GeografiaCaso({ id, tipo, guiaId, fechaDeteccion, pruebas, circulacion,
  revisionDatos = 0, actualizacionActiva = false }: {
  id: number;
  tipo: string;
  guiaId: string | null;
  fechaDeteccion: string | null;
  actualizacionActiva?: boolean;
  pruebas: PruebaDesvioVista[];
  circulacion: CirculacionVista | null;
  revisionDatos?: number;
}) {
  const esDesvio = tipo === "DESVIO_RUTA" || tipo === "DESVIO_DE_RUTA";
  const esSinGuia = tipo === "VIAJE_SIN_GUIA" || tipo === "SIN_GUIA";
  const [rutas, setRutas] = useState<RutaAutorizada[]>([]);
  const [rutasEvidencia, setRutasEvidencia] = useState<RutaAutorizada[]>([]);
  const [ventanaGps, setVentanaGps] = useState<VentanaViaje | null>(null);
  const ventanaDesde = ventanaGps?.desde ?? null;
  const ventanaHasta = ventanaGps?.hasta ?? null;
  const ultimaMarcaGps = useRef<number | null>(null);
  const [vista, setVista] = useState<"evidencia" | "posterior" | "completo">("evidencia");
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
  const referenciaGps = primeraConfirmacionGps(pruebas);
  const posteriores = soloPosteriores(puntos, referenciaGps);
  const seleccionar = (indice: number) => {
    setVista("evidencia");
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
          const [rutasResultado, evidenciaResultado, ventanaResultado] = await Promise.allSettled([
            consultar<RutaAutorizada[]>(id, "rutas", null, ctrl.signal),
            consultar<RutaAutorizada[]>(id, "rutas-evidencia", null, ctrl.signal),
            consultar<VentanaViaje>(id, "ventana", null, ctrl.signal),
          ]);
          if (ctrl.signal.aborted) return;
          if (rutasResultado.status === "fulfilled") setRutas(rutasResultado.value);
          else setAviso("No se pudieron consultar las rutas actualmente autorizadas.");
          if (evidenciaResultado.status === "fulfilled") setRutasEvidencia(evidenciaResultado.value);
          else {
            setRutasEvidencia([]);
            setError("No se pudieron recuperar las geometrías archivadas de la evidencia. Se conservan las dos mediciones y sus distancias registradas.");
          }
          if (ventanaResultado.status === "fulfilled" && ventanaResultado.value.desde && guiaId) {
            try {
              if (ventanaResultado.value.idGuia !== guiaId) throw new Error("La Guía del expediente no coincide con el viaje.");
              // Reusar UNA consulta para recorrido completo y seguimiento posterior.
              // No usar fechaDeteccion porque corresponde a la apertura administrativa del expediente.
              setVentanaGps(ventanaResultado.value);
              const gps = await gpsDelViaje(guiaId, ventanaResultado.value.desde, ventanaResultado.value.hasta, ctrl.signal);
              if (ctrl.signal.aborted) return;
              setPuntos(gps.puntos);
              ultimaMarcaGps.current = gps.puntos.length
                ? Date.parse(gps.puntos[gps.puntos.length - 1].fechaHora) : null;
              if (gps.incompleto) setAviso("Se alcanzó el límite de 10.000 posiciones del viaje; el recorrido mostrado es parcial.");
            } catch (e) {
              if (!ctrl.signal.aborted) setAviso(`No se pudo cargar el seguimiento posterior: ${(e as Error).message}`);
            }
          } else {
            setVentanaGps(null);
            setPuntos([]);
            ultimaMarcaGps.current = null;
            setAviso(ventanaResultado.status === "rejected"
              ? "No se pudo obtener el período del viaje. La evidencia registrada sigue disponible."
              : "No hay fechas válidas para consultar el seguimiento posterior.");
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
  }, [id, guiaId, fechaDeteccion, esDesvio, esSinGuia, recarga, revisionDatos]);

  // Actualiza solo el delta GPS del viaje, con pequeño solapamiento para mediciones retrasadas.
  // Las vistas completa y posterior se derivan de la misma lista, sin alterar evidencia archivada.
  // La pausa de EvidenciasEnVivo también detiene esta actualización.
  useEffect(() => {
    if (!esDesvio || !actualizacionActiva || cargando || !guiaId || !ventanaDesde) return;
    const inicio = Date.parse(ventanaDesde);
    if (!Number.isFinite(inicio)) return;
    const ctrl = new AbortController();
    let consultando = false;
    const actualizar = async () => {
      if (document.hidden || consultando) return;
      const cursor = ultimaMarcaGps.current;
      const desde = new Date(cursor === null ? inicio : Math.max(inicio, cursor - SOLAPAMIENTO_MS)).toISOString();
      const hasta = ventanaHasta ? Date.parse(ventanaHasta) : Date.now() + 1000;
      if (Date.parse(desde) >= hasta) return;
      consultando = true;
      try {
        const gps = await gpsDelViaje(guiaId, desde, ventanaHasta, ctrl.signal);
        if (ctrl.signal.aborted) return;
        if (gps.puntos.length) {
          ultimaMarcaGps.current = Math.max(ultimaMarcaGps.current ?? 0,
            ...gps.puntos.map(p => Date.parse(p.fechaHora)));
          setPuntos(anteriores => unirPosiciones(anteriores, gps.puntos).slice(-MAX_PUNTOS));
        }
        if (gps.incompleto) setAviso("Se alcanzó el límite de consulta de posiciones GPS.");
      } catch {
        if (!ctrl.signal.aborted) setAviso("El recorrido GPS no se pudo actualizar; se mantienen las posiciones cargadas.");
      } finally { consultando = false; }
    };
    const timer = window.setInterval(() => { void actualizar(); }, 15000);
    return () => { window.clearInterval(timer); ctrl.abort(); };
  }, [id, guiaId, esDesvio, actualizacionActiva, cargando, ventanaDesde, ventanaHasta]);

  if (!esDesvio && !esSinGuia) return null;
  const pruebaVerificada = referenciasDePrueba(pruebaActual, rutasEvidencia);
  const mapaVisible = esDesvio
    ? vista === "evidencia" ? Boolean(pruebaActual) : (rutas.length > 0 || (vista === "posterior" ? posteriores.length > 0 : puntos.length > 0))
    : esSinGuia && puntos.length > 0;
  return <section className="flex flex-col gap-3">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <h2 className="text-lg font-semibold text-black dark:text-zinc-50">
        {esDesvio ? "Evidencia y seguimiento del desvío" : "Circulación detectada sin Guía"}
      </h2>
      <button type="button" onClick={() => setRecarga(x => x + 1)}
        className="rounded-lg border border-zinc-300 px-3 py-1.5 text-xs hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-900">
        Recargar datos del mapa
      </button>
    </div>
    <div className="rounded-2xl bg-white p-5 text-sm shadow-sm dark:bg-zinc-950">
      {esSinGuia && <p className="mb-4 text-zinc-600 dark:text-zinc-400">
        El caso agrupa los eventos de circulación sin guía del vehículo y puede incluir varios viajes.
        Las detenciones y los períodos sin GPS no determinan un fin de viaje.
      </p>}
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
          La evidencia registrada al confirmar un desvío no cambia cuando llegan nuevas mediciones.
          El seguimiento y el recorrido operativo son vistas de contexto: nunca reemplazan las confirmaciones archivadas.
        </p>
        <div className="grid gap-3 rounded-xl bg-zinc-50 p-3 sm:grid-cols-3 dark:bg-zinc-900">
          <p><span className="block text-xs text-zinc-500">Confirmaciones archivadas</span>
            <b>{pruebas.length.toLocaleString("es-UY")}</b></p>
          <p><span className="block text-xs text-zinc-500">Mayor separación registrada</span>
            <b>{mayorSeparacion === null ? "-" : metros(mayorSeparacion)}</b></p>
          <p><span className="block text-xs text-zinc-500">Posiciones posteriores a la primera confirmación</span>
            <b>{posteriores.length.toLocaleString("es-UY")}</b></p>
        </div>
        <div role="tablist" aria-label="Vistas geográficas del caso" className="flex flex-wrap gap-2">
          <button type="button" role="tab" aria-selected={vista === "evidencia"}
            onClick={() => { setVista("evidencia"); verTodo(); }}
            className={`rounded-lg border px-3 py-2 text-sm ${vista === "evidencia" ? "border-orange-600 bg-orange-50 font-semibold text-orange-900 dark:bg-orange-950 dark:text-orange-200" : "hover:bg-zinc-100 dark:hover:bg-zinc-900"}`}>
            Evidencia registrada
          </button>
          <button type="button" role="tab" aria-selected={vista === "posterior"}
            onClick={() => { setVista("posterior"); verTodo(); }}
            className={`rounded-lg border px-3 py-2 text-sm ${vista === "posterior" ? "border-sky-600 bg-sky-50 font-semibold text-sky-900 dark:bg-sky-950 dark:text-sky-200" : "hover:bg-zinc-100 dark:hover:bg-zinc-900"}`}>
            Seguimiento posterior
          </button>
          <button type="button" role="tab" aria-selected={vista === "completo"}
            onClick={() => { setVista("completo"); verTodo(); }}
            className={`rounded-lg border px-3 py-2 text-sm ${vista === "completo" ? "border-slate-600 bg-slate-50 font-semibold text-slate-900 dark:bg-slate-900 dark:text-slate-200" : "hover:bg-zinc-100 dark:hover:bg-zinc-900"}`}>
            Recorrido completo
          </button>
        </div>
        {vista === "evidencia" && (pruebaActual ? <div className="rounded-xl border border-orange-200 p-3 dark:border-orange-900" aria-live="polite">
          <p className="font-medium">Confirmación archivada {indiceActual + 1} de {pruebas.length}</p>
          <p className="mt-1 text-xs text-zinc-500">Registrada: {fechaHora(pruebaActual.fechaHora)}</p>
          <div className="mt-2 grid gap-2 sm:grid-cols-2">
            <p><span className="block text-zinc-500">Primera posición (amarilla)</span>
              <b>{metros(pruebaActual.distanciaParejaMetros)}</b> de las rutas evaluadas</p>
            <p><span className="block text-zinc-500">Segunda posición (roja)</span>
              <b>{metros(pruebaActual.distanciaConfirmanteMetros)}</b> de las rutas evaluadas</p>
          </div>
          <p className="mt-2 text-xs text-zinc-600 dark:text-zinc-400">
            Límite configurado: {metros(pruebaActual.umbralMetros)}.
            Intervalo entre mediciones: {Math.round((Date.parse(pruebaActual.fechaHora) - Date.parse(pruebaActual.fechaPareja)) / 1000)} s
            (máximo {pruebaActual.ventanaMaximaSegundos} s).
          </p>
          <p className="mt-2 text-xs text-zinc-500">
            Rutas históricas verificadas: {pruebaVerificada.rutas.length} de {new Set(pruebaActual.hashesRutas).size}.
          </p>
          {pruebaVerificada.faltantes > 0 && <p className="mt-2 text-amber-700 dark:text-amber-300" role="status">
            {pruebaVerificada.faltantes} referencia(s) histórica(s) no están disponibles o no pudieron verificarse.
            No se sustituyen por las rutas actuales.
          </p>}
        </div> : <p>No hay confirmaciones geométricas persistidas para este expediente.</p>)}
        {vista === "posterior" && <div className="rounded-xl border border-sky-200 p-3 text-sm dark:border-sky-900">
          <p className="font-medium">Circulación posterior a la primera confirmación GPS</p>
          {referenciaGps ? <p className="mt-1 text-zinc-600 dark:text-zinc-400">
            Referencia operativa: <b>{fechaHora(referenciaGps)}</b> (primera confirmación archivada).
            Solo se muestran posiciones GPS estrictamente posteriores a ese instante y dentro del período de la Guía.
          </p> : <p className="mt-1 text-amber-700 dark:text-amber-300">
            Sin confirmación GPS válida no se puede delimitar el seguimiento posterior.
          </p>}
          <p className="mt-1 text-xs text-zinc-500">
            Apertura administrativa del caso: {fechaHora(fechaDeteccion)}. Esta fecha NO se usa para dividir el recorrido GPS.
            Algunas de estas mediciones también pueden integrar otras confirmaciones archivadas.
            Las rutas de esta vista son las actualmente autorizadas, no las rutas históricas de la evidencia.
            {actualizacionActiva ? " Nuevas posiciones se consultan automáticamente cada 15 s." : " La actualización está pausada o el caso fue resuelto."}
          </p>
        </div>}
        {vista === "completo" && <div className="rounded-xl border border-slate-200 p-3 text-sm dark:border-slate-700">
          <p className="font-medium">Recorrido operativo completo del viaje</p>
          <p className="mt-1 text-zinc-600 dark:text-zinc-400">
            {puntos.length.toLocaleString("es-UY")} posiciones GPS consultadas para la Guía, anteriores y posteriores a la primera confirmación.
            Son contexto operativo: <b>no sustituyen las confirmaciones probatorias archivadas</b>.
            Las rutas dibujadas son las autorizadas actualmente.
          </p>
        </div>}
      </div>}
      {cargando && <p role="status" className="py-4 text-zinc-500">Cargando información geográfica…</p>}
      {error && <p role="alert" className="mb-3 rounded-lg bg-amber-50 p-3 text-amber-800 dark:bg-amber-950 dark:text-amber-300">{error}</p>}
      {aviso && <p role="status" className="mb-3 rounded-lg bg-amber-50 p-3 text-amber-800 dark:bg-amber-950 dark:text-amber-300">Historial parcial: {aviso}</p>}
      {mapaVisible ? <>
        {esDesvio && <div className="mb-3 flex flex-wrap items-center gap-x-4 gap-y-2">
          <label className="flex cursor-pointer items-center gap-2">
            <input type="checkbox" checked={mostrarRutas} onChange={e => setMostrarRutas(e.target.checked)} />
            <span>{vista === "evidencia"
              ? `Rutas históricas verificadas (${pruebaVerificada.rutas.length})`
              : `Rutas actuales (${rutas.length})`}</span>
          </label>
          {vista !== "evidencia" && <label className="flex cursor-pointer items-center gap-2">
            <input type="checkbox" checked={mostrarRecorrido} onChange={e => setMostrarRecorrido(e.target.checked)} />
            <span>{vista === "posterior" ? `Posiciones posteriores (${posteriores.length})` : `Recorrido GPS completo (${puntos.length})`}</span>
          </label>}
          <button type="button" onClick={verTodo}
            className="rounded-lg border px-3 py-1.5 text-xs hover:bg-zinc-100 dark:hover:bg-zinc-900">
            Ajustar mapa al contenido
          </button>
          {guiaId && <Link className="text-xs text-sky-700 underline dark:text-sky-300"
            href={`/viajes/${encodeURIComponent(guiaId)}`}>Ver recorrido operativo completo</Link>}
        </div>}
        <MapaEvidencia key={esDesvio ? vista : "sin-guia"}
          rutas={esDesvio && vista === "evidencia" ? pruebaVerificada.rutas : rutas}
          puntos={esDesvio && vista === "evidencia" ? [] : esDesvio && vista === "posterior" ? posteriores : puntos}
          pruebas={esDesvio && vista === "evidencia" && pruebaActual ? [pruebaActual] : []}
          contexto={esDesvio ? vista === "evidencia" ? "evidencia" : "seguimiento" : "sin-guia"}
          mostrarRutas={!esDesvio || mostrarRutas}
          mostrarRecorrido={esDesvio && vista === "evidencia" ? false : !esDesvio || mostrarRecorrido}
          enfocar={revisionFoco} modoEncuadre={modoEncuadre} />
        <p className="mt-2 text-xs text-zinc-500">
          {esDesvio && vista === "evidencia"
            ? "La prueba está fijada en el expediente: amarillo y rojo señalan las dos mediciones utilizadas en esta confirmación. Las rutas se resuelven por sus huellas históricas, no por la selección vigente."
            : esDesvio
              ? vista === "posterior"
                ? "Naranja discontinuo: GPS posteriores a la primera confirmación archivada. Verde/azul/violeta: rutas autorizadas actualmente."
                : "Naranja discontinuo: recorrido GPS completo de la Guía. No sustituye la evidencia archivada. Verde/azul/violeta: rutas autorizadas actualmente."
              : `GPS del expediente: ${puntos.length}. Verde: primera posición; rojo: última posición observada.`}
        </p>
        {esDesvio && vista === "posterior" && posteriores.length === 0 && !aviso && <p className="mt-2 text-xs text-zinc-500">
          No se recuperaron GPS posteriores a la primera confirmación dentro del intervalo de la Guía.
          Podés consultar el recorrido completo en la otra pestaña, sin incorporarlo a la evidencia.
        </p>}
      </> : !cargando && !error && <p className="py-4 text-zinc-500">
        {esDesvio && vista === "posterior" ? "Todavía no hay posiciones posteriores ni rutas actuales para representar." : "No hay coordenadas disponibles para mostrar."}
      </p>}
      {esDesvio && vista === "evidencia" && pruebas.length > 0 && <div className="mt-5">
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

"use client";

import dynamic from "next/dynamic";
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { BASE_PATH } from "@/lib/base-path";
import { referenciasDePrueba } from "@/lib/evidencia-desvio";
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
 * El mapa combina el historial completo con las coordenadas de la confirmación seleccionada,
 * conservando las rutas archivadas por su huella como referencia de la evidencia. */
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
  const [rutasEvidencia, setRutasEvidencia] = useState<RutaAutorizada[]>([]);
  const [ventanaGps, setVentanaGps] = useState<VentanaViaje | null>(null);
  const ventanaDesde = ventanaGps?.desde ?? null;
  const ventanaHasta = ventanaGps?.hasta ?? null;
  const ultimaMarcaGps = useRef<number | null>(null);
  const [puntos, setPuntos] = useState<PuntoGpsSeguimiento[]>([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);
  const [recarga, setRecarga] = useState(0);
  const [pagina, setPagina] = useState<number | null>(null);
  const [claveSeleccionada, setClaveSeleccionada] = useState<string | null>(null);
  const [revisionFoco, setRevisionFoco] = useState(0);
  const [modoEncuadre, setModoEncuadre] = useState<"todo" | "confirmacion">("todo");
  const [mostrarRutas, setMostrarRutas] = useState(true);
  const [mostrarRecorrido, setMostrarRecorrido] = useState(true);
  const totalPaginas = Math.max(1, Math.ceil(pruebas.length / CONFIRMACIONES_POR_PAGINA));
  const paginaActual = Math.min(pagina ?? totalPaginas, totalPaginas);
  // Sin selección manual, seguir el último par; conservar por identidad una prueba histórica elegida.
  const clavePrueba = (p: PruebaDesvioVista) => `${p.fechaPareja}|${p.fechaHora}|${p.latConfirmante}|${p.lonConfirmante}`;
  const ultimoIndice = pruebas.length - 1;
  const indiceBuscado = claveSeleccionada === null ? ultimoIndice : pruebas.findIndex(p => clavePrueba(p) === claveSeleccionada);
  const indiceActual = indiceBuscado < 0 ? ultimoIndice : indiceBuscado;
  const pruebaActual = pruebas[indiceActual];
  const paginaInicio = (paginaActual - 1) * CONFIRMACIONES_POR_PAGINA;
  const seleccionar = (indice: number) => {
    setClaveSeleccionada(indice === ultimoIndice ? null : clavePrueba(pruebas[indice]));
    if (indice === ultimoIndice) setPagina(null);
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
          const [evidenciaResultado, ventanaResultado] = await Promise.allSettled([
            consultar<RutaAutorizada[]>(id, "rutas-evidencia", null, ctrl.signal),
            consultar<VentanaViaje>(id, "ventana", null, ctrl.signal),
          ]);
          if (ctrl.signal.aborted) return;
          if (evidenciaResultado.status === "fulfilled") setRutasEvidencia(evidenciaResultado.value);
          else {
            setRutasEvidencia([]);
            setError("No se pudieron recuperar las geometrías archivadas de la evidencia. Se conservan las dos mediciones y sus distancias registradas.");
          }
          if (ventanaResultado.status === "fulfilled" && ventanaResultado.value.desde && guiaId) {
            try {
              if (ventanaResultado.value.idGuia !== guiaId) throw new Error("La Guía del expediente no coincide con el viaje.");
              // Consultar el recorrido completo dentro de la ventana de esta Guía.
              // No usar fechaDeteccion porque corresponde a la apertura administrativa del expediente.
              setVentanaGps(ventanaResultado.value);
              const gps = await gpsDelViaje(guiaId, ventanaResultado.value.desde, ventanaResultado.value.hasta, ctrl.signal);
              if (ctrl.signal.aborted) return;
              setPuntos(gps.puntos);
              ultimaMarcaGps.current = gps.puntos.length
                ? Date.parse(gps.puntos[gps.puntos.length - 1].fechaHora) : null;
              if (gps.incompleto) setAviso("Se alcanzó el límite de 10.000 posiciones del viaje; el recorrido mostrado es parcial.");
            } catch (e) {
              if (!ctrl.signal.aborted) setAviso(`No se pudo cargar el recorrido GPS: ${(e as Error).message}`);
            }
          } else {
            setVentanaGps(null);
            setPuntos([]);
            ultimaMarcaGps.current = null;
            setAviso(ventanaResultado.status === "rejected"
              ? "No se pudo obtener el período del viaje. La evidencia registrada sigue disponible."
              : "No hay fechas válidas para consultar el recorrido GPS.");
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
  // El recorrido se actualiza sin alterar la confirmación ni las rutas archivadas.
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
  const mapaVisible = puntos.length > 0 || (esDesvio && Boolean(pruebaActual));
  return <section className="flex flex-col gap-3">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <h2 className="text-lg font-semibold text-foreground">
        {esDesvio ? "Evidencia del desvío" : "Circulación detectada sin Guía"}
      </h2>
      <button type="button" onClick={() => setRecarga(x => x + 1)}
        className="rounded-lg border border-input px-3 py-1.5 text-xs hover:bg-accent">
        Recargar datos del mapa
      </button>
    </div>
    <div className="rounded-xl border bg-card p-5 text-sm shadow-sm">
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
      {cargando && <p role="status" className="py-4 text-muted-foreground">Cargando información geográfica…</p>}
      {error && <p role="alert" className="mb-3 rounded-lg bg-amber-50 p-3 text-amber-800 dark:bg-amber-950 dark:text-amber-300">{error}</p>}
      {aviso && <p role="status" className="mb-3 rounded-lg bg-amber-50 p-3 text-amber-800 dark:bg-amber-950 dark:text-amber-300">Historial parcial: {aviso}</p>}
      {esDesvio && pruebaVerificada.faltantes > 0 && <p role="status" className="mb-3 text-amber-700 dark:text-amber-300">
        No se pudieron recuperar {pruebaVerificada.faltantes} rutas de la confirmación seleccionada.
      </p>}
      {mapaVisible ? <>
        {esDesvio && <div className="mb-3 flex flex-wrap items-center gap-x-4 gap-y-2">
          <label className="flex cursor-pointer items-center gap-2">
            <input type="checkbox" checked={mostrarRutas} onChange={e => setMostrarRutas(e.target.checked)} />
            <span>Rutas evaluadas ({pruebaVerificada.rutas.length})</span>
          </label>
          <label className="flex cursor-pointer items-center gap-2">
            <input type="checkbox" checked={mostrarRecorrido} onChange={e => setMostrarRecorrido(e.target.checked)} />
            <span>Recorrido GPS ({puntos.length})</span>
          </label>
          <button type="button" onClick={verTodo}
            className="rounded-lg border px-3 py-1.5 text-xs hover:bg-accent">
            Ajustar mapa al contenido
          </button>
          {guiaId && <Link className="text-xs text-sky-700 underline dark:text-sky-300"
            href={`/viajes/${encodeURIComponent(guiaId)}`}>Ver viaje</Link>}
        </div>}
        <MapaEvidencia rutas={esDesvio ? pruebaVerificada.rutas : []}
          puntos={puntos} pruebas={esDesvio && pruebaActual ? [pruebaActual] : []}
          contexto={esDesvio ? "evidencia" : "sin-guia"}
          mostrarRutas={!esDesvio || mostrarRutas}
          mostrarRecorrido={!esDesvio || mostrarRecorrido}
          enfocar={revisionFoco} modoEncuadre={modoEncuadre} />
        <p className="mt-2 text-xs text-muted-foreground">
          {esDesvio
            ? "Naranja: recorrido GPS. Amarillo y rojo: mediciones de la confirmación seleccionada."
            : `GPS del expediente: ${puntos.length}. Verde: primera posición; rojo: última posición observada.`}
        </p>
      </> : !cargando && !error && <p className="py-4 text-muted-foreground">
        No hay coordenadas disponibles para mostrar.
      </p>}
      {esDesvio && pruebas.length > 0 && <div className="mt-5">
        <h3 className="mb-2 font-medium">Historial de confirmaciones ({pruebas.length})</h3>
        <p className="mb-2 text-xs text-muted-foreground">Seleccioná una fila para localizar sus dos posiciones en el mapa.</p>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs sm:text-sm">
            <thead className="border-b border-border text-muted-foreground">
              <tr><th className="py-2 pr-2 font-medium">N.º</th>
                <th className="py-2 pr-2 font-medium">Hora</th>
                <th className="py-2 pr-2 text-right font-medium">Separación</th>
                <th className="py-2 text-right font-medium">Mapa</th></tr>
            </thead>
            <tbody>{pruebas.slice(paginaInicio, paginaInicio + CONFIRMACIONES_POR_PAGINA).map((prueba, i) => {
              const indice = paginaInicio + i;
              return <tr key={`${indice}-${prueba.fechaHora}`} className={`border-b border-border ${indice === indiceActual ? "bg-orange-50 dark:bg-orange-950/30" : ""}`}>
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
          <span className="text-xs text-muted-foreground">Página {paginaActual} de {totalPaginas}</span>
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

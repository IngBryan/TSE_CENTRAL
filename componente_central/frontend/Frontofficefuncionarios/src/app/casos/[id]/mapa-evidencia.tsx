"use client";

import { useEffect, useRef, useState } from "react";
import dynamic from "next/dynamic";
import { Map as MapaGL, NavigationControl, LngLatBounds, setWorkerUrl, type GeoJSONSource } from "maplibre-gl";
import type { FeatureCollection, LineString, Point } from "geojson";
import "maplibre-gl/dist/maplibre-gl.css";
import { coordenadasRuta, type Coordenada, type PuntoGpsSeguimiento, type RutaAutorizada } from "@/lib/seguimiento";
import type { PruebaDesvioVista } from "@/lib/casos";

setWorkerUrl(new URL("maplibre-gl/dist/maplibre-gl-worker.mjs", import.meta.url).toString());
const ESTILO = "https://tiles.openfreemap.org/styles/liberty";
const VACIO: FeatureCollection = { type: "FeatureCollection", features: [] };
const COLORES_RUTA = ["#16a34a", "#2563eb", "#a855f7"];
const coordenadaGps = (p: PuntoGpsSeguimiento): Coordenada => [p.longitud, p.latitud];
const valida = (c: Coordenada): boolean => Number.isFinite(c[0]) && Number.isFinite(c[1])
  && Math.abs(c[0]) <= 180 && Math.abs(c[1]) <= 90;
const MapaEvidencia2D = dynamic(() => import("./mapa-evidencia-2d"), { ssr: false });

export type MapaEvidenciaProps = {
  rutas: RutaAutorizada[];
  puntos: PuntoGpsSeguimiento[];
  pruebas: PruebaDesvioVista[];
  mostrarRutas?: boolean;
  mostrarRecorrido?: boolean;
  enfocar?: number;
  modoEncuadre?: "todo" | "confirmacion";
  contexto?: "evidencia" | "seguimiento" | "sin-guia";
};

/** Dos usos excluyentes: la prueba inmutable (rutas por SHA + par de coordenadas)
 * y el seguimiento posterior. No mezcla sus capas en una única vista probatoria. */
export default function MapaEvidencia(props: MapaEvidenciaProps) {
  const { rutas, puntos, pruebas, mostrarRutas = true, mostrarRecorrido = true,
    enfocar = 0, modoEncuadre = "todo", contexto = "seguimiento" } = props;
  const nodo = useRef<HTMLDivElement | null>(null);
  const mapa = useRef<MapaGL | null>(null);
  const vistaInicial = useRef(false);
  const [listo, setListo] = useState(false);
  const [error, setError] = useState(false);
  const [usar2D, setUsar2D] = useState(() => {
    try { return document.createElement("canvas").getContext("webgl2") === null; }
    catch { return true; }
  });

  useEffect(() => {
    if (!nodo.current || usar2D) return;
    let m: MapaGL;
    try {
      m = new MapaGL({ container: nodo.current, style: ESTILO,
        center: [-56.2, -33.5], zoom: 6 });
    } catch {
      // La falta de WebGL no debe impedir consultar el expediente ni su recorrido.
      queueMicrotask(() => setUsar2D(true));
      return;
    }
    mapa.current = m;
    m.addControl(new NavigationControl(), "top-right");
    m.on("load", () => {
      for (const id of ["rutas", "trayectoria", "confirmaciones"]) {
        m.addSource(id, { type: "geojson", data: VACIO });
      }
      m.addLayer({ id: "corredores", type: "line", source: "rutas",
        paint: { "line-color": ["get", "color"], "line-width": 5, "line-opacity": 0.8 } });
      m.addLayer({ id: "circulacion-gps", type: "line", source: "trayectoria",
        paint: { "line-color": "#ea580c", "line-width": 4, "line-dasharray": [2, 1] } });
      m.addLayer({ id: "puntos-confirmacion", type: "circle", source: "confirmaciones",
        paint: { "circle-radius": 8, "circle-color": ["get", "color"],
          "circle-stroke-width": 2, "circle-stroke-color": "#ffffff" } });
      setListo(true);
    });
    m.on("error", () => setError(true));
    return () => { mapa.current = null; m.remove(); };
  }, [usar2D]);

  useEffect(() => {
    const m = mapa.current;
    if (!m || !listo) return;
    const rutasValidas = rutas.flatMap((r, i) => {
      const coords = coordenadasRuta(r);
      return coords ? [{ type: "Feature" as const, properties: { color: COLORES_RUTA[i % 3] },
        geometry: { type: "LineString" as const, coordinates: coords } }] : [];
    });
    const coordsGps = puntos.map(coordenadaGps).filter(valida);
    const rutasFc: FeatureCollection<LineString> = {
      type: "FeatureCollection", features: mostrarRutas ? rutasValidas : [],
    };
    const gpsFc: FeatureCollection<LineString> = { type: "FeatureCollection", features: mostrarRecorrido && coordsGps.length >= 2
      ? [{ type: "Feature", properties: {}, geometry: { type: "LineString", coordinates: coordsGps } }] : [] };
    const seleccionadas = pruebas.flatMap<{ coord: Coordenada; color: string }>(p => [
      { coord: [p.lonPareja, p.latPareja], color: "#eab308" },
      { coord: [p.lonConfirmante, p.latConfirmante], color: "#dc2626" },
    ]);
    // En casos SIN_GUIA no existen confirmaciones; marcamos el inicio y fin del historial.
    const extremos: { coord: Coordenada; color: string }[] = !pruebas.length && coordsGps.length
      ? [{ coord: coordsGps[0], color: "#16a34a" },
         { coord: coordsGps[coordsGps.length - 1], color: "#dc2626" }] : [];
    const marcas: FeatureCollection<Point> = { type: "FeatureCollection", features:
      [...seleccionadas, ...extremos].filter(p => valida(p.coord)).map(p => ({
        type: "Feature", properties: { color: p.color },
        geometry: { type: "Point", coordinates: p.coord },
      })),
    };
    void (m.getSource("rutas") as GeoJSONSource).setData(rutasFc);
    void (m.getSource("trayectoria") as GeoJSONSource).setData(gpsFc);
    void (m.getSource("confirmaciones") as GeoJSONSource).setData(marcas);
    const todas: Coordenada[] = [
      ...rutasValidas.flatMap(f => f.geometry.coordinates as Coordenada[]),
      ...coordsGps,
      ...marcas.features.map(f => f.geometry.coordinates as Coordenada),
    ];
    // Enfocar solo cambia la cámara: el resto del recorrido continúa dibujado.
    const seleccion = seleccionadas.map(p => p.coord).filter(valida);
    const foco = modoEncuadre === "confirmacion" && seleccion.length ? seleccion : todas;
    if (foco.length && (!vistaInicial.current || enfocar > 0)) {
      const bounds = new LngLatBounds();
      foco.forEach(c => bounds.extend(c));
      m.fitBounds(bounds, { padding: 48, maxZoom: 13, duration: 0 });
      vistaInicial.current = true;
    }
  }, [listo, rutas, puntos, pruebas, mostrarRutas, mostrarRecorrido, enfocar, modoEncuadre]);

  if (usar2D) return <MapaEvidencia2D {...props} />;

  return <div>
    <div ref={nodo} role="img" aria-label={contexto === "evidencia"
      ? "Evidencia registrada: dos mediciones del desvío y geometrías de referencia archivadas"
      : contexto === "sin-guia" ? "Posiciones vinculadas a la circulación sin guía"
      : "Recorrido GPS posterior a la detección y rutas actualmente autorizadas"}
      className="h-[370px] w-full rounded-xl border border-black/10 dark:border-white/10 sm:h-[480px]" />
    {error && <p role="alert" className="mt-2 text-xs text-amber-700 dark:text-amber-300">
      El mapa base podría no estar disponible. Revisá la conexión con el proveedor cartográfico.
    </p>}
  </div>;
}

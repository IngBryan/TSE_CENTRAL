"use client";

import { Component, useEffect, useRef, useState, type ReactNode } from "react";
import { Map as MapaGL, NavigationControl, LngLatBounds, setWorkerUrl, type GeoJSONSource } from "maplibre-gl";
import type { FeatureCollection, LineString } from "geojson";
import "maplibre-gl/dist/maplibre-gl.css";
import type { RutaAsignada, RutaVista } from "./tipos-rutas";

setWorkerUrl(new URL("maplibre-gl/dist/maplibre-gl-worker.mjs", import.meta.url).toString());
const ESTILO = "https://tiles.openfreemap.org/styles/liberty";
const VACIO: FeatureCollection = { type: "FeatureCollection", features: [] };
type RutaMapeable = { alternativa: number; geoJson?: string; geometriaGeoJson?: string };

function coordenadas(texto?: string): [number, number][] | null {
  if (!texto) return null;
  try {
    const geo: unknown = JSON.parse(texto);
    if (!geo || typeof geo !== "object" || !("type" in geo) || geo.type !== "LineString"
      || !("coordinates" in geo) || !Array.isArray(geo.coordinates)) return null;
    const puntos = geo.coordinates as unknown[];
    if (puntos.length < 2 || !puntos.every(p => Array.isArray(p) && p.length >= 2
      && typeof p[0] === "number" && Number.isFinite(p[0]) && Math.abs(p[0]) <= 180
      && typeof p[1] === "number" && Number.isFinite(p[1]) && Math.abs(p[1]) <= 90)) return null;
    return puntos.map(p => [(p as number[])[0], (p as number[])[1]]);
  } catch { return null; }
}

type PropsMapa = {
  rutas: (RutaVista | RutaAsignada)[];
};

// El mapa es una ayuda visual: un navegador sin soporte grafico no debe impedir asignar el viaje.
class LimiteMapa extends Component<{ children: ReactNode }, { fallo: boolean }> {
  state = { fallo: false };

  static getDerivedStateFromError() {
    return { fallo: true };
  }

  render() {
    if (this.state.fallo) return <p role="status" className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900 dark:bg-amber-950 dark:text-amber-200">
      No se pudo mostrar el mapa en este navegador. Los datos de la ruta siguen disponibles para revisar y confirmar.
    </p>;
    return this.props.children;
  }
}

export default function MapaRutas(props: PropsMapa) {
  return <LimiteMapa><ContenidoMapa {...props} /></LimiteMapa>;
}

function ContenidoMapa({ rutas }: PropsMapa) {
  const nodo = useRef<HTMLDivElement | null>(null);
  const mapa = useRef<MapaGL | null>(null);
  const [listo, setListo] = useState(false);
  const [error, setError] = useState(false);
  useEffect(() => {
    if (!nodo.current) return;
    const m = new MapaGL({ container: nodo.current, style: ESTILO,
      center: [-56.2, -33.5], zoom: 6 });
    mapa.current = m;
    m.addControl(new NavigationControl(), "top-right");
    m.on("load", () => {
      m.addSource("rutas-previa", { type: "geojson", data: VACIO });
      m.addLayer({ id: "rutas-previa-lineas", type: "line", source: "rutas-previa",
        paint: { "line-color": ["get", "color"], "line-width": 5, "line-opacity": 0.88 } });
      setListo(true);
    });
    m.on("error", () => setError(true));
    return () => { mapa.current = null; m.remove(); };
  }, []);

  useEffect(() => {
    const m = mapa.current;
    if (!m || !listo) return;
    const lineas = rutas.slice(0, 1).flatMap((ruta) => {
      const r = ruta as RutaMapeable;
      const coords = coordenadas(r.geoJson ?? r.geometriaGeoJson);
      return coords ? [{ type: "Feature" as const,
        properties: { color: "#16a34a" },
        geometry: { type: "LineString" as const, coordinates: coords } }] : [];
    });
    const coleccion: FeatureCollection<LineString> = { type: "FeatureCollection", features: lineas };
    void (m.getSource("rutas-previa") as GeoJSONSource).setData(coleccion);
    const bounds = new LngLatBounds();
    const todos = lineas.flatMap(l => l.geometry.coordinates);
    todos.forEach(coord => bounds.extend([coord[0], coord[1]]));
    if (todos.length) m.fitBounds(bounds, { padding: 45, maxZoom: 12, duration: 0 });
  }, [rutas, listo]);

  return <div>
    <div ref={nodo} role="img" aria-label="Mapa de la ruta admitida"
      className="h-[340px] rounded-xl border border-zinc-200 sm:h-[450px] dark:border-zinc-800" />
    {error && <p className="mt-2 text-xs text-amber-800 dark:text-amber-200">
      No se pudo cargar completamente el mapa base. Revisá la conexión con el proveedor cartográfico.
    </p>}
  </div>;
}

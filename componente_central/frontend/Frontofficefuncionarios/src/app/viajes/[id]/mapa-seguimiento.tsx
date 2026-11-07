"use client";

import { useEffect, useRef, useState } from "react";
import { Map as MapaGL, NavigationControl, LngLatBounds, setWorkerUrl, type GeoJSONSource } from "maplibre-gl";
import type { FeatureCollection, LineString, Point } from "geojson";
import "maplibre-gl/dist/maplibre-gl.css";
import { type Coordenada, type PuntoGpsSeguimiento } from "@/lib/seguimiento";

// MapLibre v6 + Next.js 16/Turbopack: worker empaquetado por el bundler.
setWorkerUrl(new URL("maplibre-gl/dist/maplibre-gl-worker.mjs", import.meta.url).toString());

const VACIO: FeatureCollection = { type: "FeatureCollection", features: [] };
const ESTILO = "https://tiles.openfreemap.org/styles/liberty";

function coleccionTrayectoria(posiciones: PuntoGpsSeguimiento[]): FeatureCollection<LineString> {
  const coordinates: Coordenada[] = posiciones.map((p) => [p.longitud, p.latitud]);
  return {
    type: "FeatureCollection",
    features: coordinates.length >= 2 ? [{
      type: "Feature", properties: {}, geometry: { type: "LineString", coordinates },
    }] : [],
  };
}

function coleccionPosicion(posicion: PuntoGpsSeguimiento | null): FeatureCollection<Point> {
  return {
    type: "FeatureCollection",
    features: posicion ? [{
      type: "Feature", properties: {},
      geometry: { type: "Point", coordinates: [posicion.longitud, posicion.latitud] },
    }] : [],
  };
}

export default function MapaSeguimiento({
  posiciones, ultima, mostrarRecorrido, centrar, centrarRecorrido,
}: {
  posiciones: PuntoGpsSeguimiento[];
  ultima: PuntoGpsSeguimiento | null;
  mostrarRecorrido: boolean;
  centrar: number;
  centrarRecorrido: number;
}) {
  const contenedor = useRef<HTMLDivElement | null>(null);
  const mapa = useRef<MapaGL | null>(null);
  const primeraVista = useRef(false);
  const [listo, setListo] = useState(false);
  const [error, setError] = useState(false);

  useEffect(() => {
    if (!contenedor.current) return;
    const instancia = new MapaGL({
      container: contenedor.current,
      style: ESTILO,
      center: [-56.2, -33.5],
      zoom: 6,
      attributionControl: {},
    });
    mapa.current = instancia;
    instancia.addControl(new NavigationControl(), "top-right");
    instancia.on("load", () => {
      if (!mapa.current) return;
      instancia.addSource("trayectoria-gps", { type: "geojson", data: VACIO });
      instancia.addLayer({
        id: "linea-gps", type: "line", source: "trayectoria-gps",
        paint: { "line-color": "#ea580c", "line-width": 3, "line-dasharray": [2, 1] },
      });
      instancia.addSource("primera-gps", { type: "geojson", data: VACIO });
      instancia.addLayer({ id: "punto-inicial", type: "circle", source: "primera-gps",
        paint: { "circle-radius": 7, "circle-color": "#16a34a", "circle-stroke-width": 2, "circle-stroke-color": "#ffffff" } });
      instancia.addSource("ultima-gps", { type: "geojson", data: VACIO });
      instancia.addLayer({
        id: "punto-gps", type: "circle", source: "ultima-gps",
        paint: { "circle-radius": 8, "circle-color": "#ea580c", "circle-stroke-width": 2, "circle-stroke-color": "#ffffff" },
      });
      setListo(true);
    });
    instancia.on("error", () => setError(true));
    return () => {
      mapa.current = null;
      instancia.remove();
    };
  }, []);

  useEffect(() => {
    const actual = mapa.current;
    if (!listo || !actual) return;
    const gps = coleccionTrayectoria(mostrarRecorrido ? posiciones : []);
    const ultimo = coleccionPosicion(ultima);
    void (actual.getSource("trayectoria-gps") as GeoJSONSource).setData(gps);
    void (actual.getSource("ultima-gps") as GeoJSONSource).setData(ultimo);
    void (actual.getSource("primera-gps") as GeoJSONSource).setData(
      coleccionPosicion(mostrarRecorrido && posiciones.length ? posiciones[0] : null));
    if (!primeraVista.current) {
      const bounds = new LngLatBounds();
      const puntos: Coordenada[] = [
        ...posiciones.map((p): Coordenada => [p.longitud, p.latitud]),
      ];
      if (ultima) puntos.push([ultima.longitud, ultima.latitud]);
      for (const p of puntos) bounds.extend(p);
      if (puntos.length > 0) {
        actual.fitBounds(bounds, { padding: 48, maxZoom: 13, duration: 0 });
        primeraVista.current = true;
      }
    }
  }, [listo, posiciones, ultima, mostrarRecorrido]);

  useEffect(() => {
    if (centrar > 0 && ultima && mapa.current && listo) {
      mapa.current.flyTo({ center: [ultima.longitud, ultima.latitud], zoom: 13 });
    }
    // centrar es un contador de interacción; no recentrar al llegar nuevas posiciones automáticamente.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [centrar]);

  useEffect(() => {
    const actual = mapa.current;
    if (centrarRecorrido <= 0 || !listo || !actual) return;
    const coords: Coordenada[] = [
      ...(mostrarRecorrido ? posiciones.map((p): Coordenada => [p.longitud, p.latitud]) : []),
    ];
    if (!coords.length) return;
    const bounds = new LngLatBounds();
    for (const p of coords) bounds.extend(p);
    actual.fitBounds(bounds, { padding: 48, maxZoom: 13, duration: 400 });
    // Acción explícita del usuario: no recentrar ante nuevas mediciones.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [centrarRecorrido]);

  return (
    <div className="relative">
      <div ref={contenedor} className="h-[370px] w-full overflow-hidden rounded-xl border border-black/10 dark:border-white/10 sm:h-[480px]" aria-label="Mapa del recorrido GPS efectuado por el vehículo" role="img" />
      {error && (
        <p role="alert" className="mt-2 text-sm text-amber-800 dark:text-amber-300">
          No se pudo cargar completamente el mapa base. Revisá la conectividad con el proveedor de mapas.
        </p>
      )}
    </div>
  );
}

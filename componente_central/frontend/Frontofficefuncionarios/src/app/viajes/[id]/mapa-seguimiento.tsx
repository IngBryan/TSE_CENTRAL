"use client";

import { useEffect, useRef, useState } from "react";
import { Map as MapaGL, NavigationControl, LngLatBounds, setWorkerUrl, type GeoJSONSource } from "maplibre-gl";
import type { FeatureCollection, LineString, Point } from "geojson";
import "maplibre-gl/dist/maplibre-gl.css";
import {
  coordenadasRuta, type Coordenada, type PuntoGpsSeguimiento, type RutaAutorizada,
} from "@/lib/seguimiento";

// MapLibre v6 + Next.js 16/Turbopack: worker empaquetado por el bundler.
setWorkerUrl(new URL("maplibre-gl/dist/maplibre-gl-worker.mjs", import.meta.url).toString());

const VACIO: FeatureCollection = { type: "FeatureCollection", features: [] };
const COLORES = ["#16a34a", "#2563eb", "#a855f7"];
const ESTILO = "https://tiles.openfreemap.org/styles/liberty";

function coleccionRutas(rutas: RutaAutorizada[]): FeatureCollection<LineString> {
  return {
    type: "FeatureCollection",
    features: rutas.flatMap((r, i) => {
      const coordinates = coordenadasRuta(r);
      return coordinates ? [{
        type: "Feature" as const,
        properties: { color: COLORES[i % COLORES.length], alternativa: r.alternativa },
        geometry: { type: "LineString" as const, coordinates },
      }] : [];
    }),
  };
}

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
  rutas, posiciones, ultima, mostrarRutas, mostrarRecorrido, centrar,
}: {
  rutas: RutaAutorizada[];
  posiciones: PuntoGpsSeguimiento[];
  ultima: PuntoGpsSeguimiento | null;
  mostrarRutas: boolean;
  mostrarRecorrido: boolean;
  centrar: number;
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
      instancia.addSource("rutas-autorizadas", { type: "geojson", data: VACIO });
      instancia.addLayer({
        id: "lineas-autorizadas", type: "line", source: "rutas-autorizadas",
        paint: { "line-color": ["get", "color"], "line-width": 5, "line-opacity": 0.82 },
      });
      instancia.addSource("trayectoria-gps", { type: "geojson", data: VACIO });
      instancia.addLayer({
        id: "linea-gps", type: "line", source: "trayectoria-gps",
        paint: { "line-color": "#ea580c", "line-width": 3, "line-dasharray": [2, 1] },
      });
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
    const trazado = coleccionRutas(mostrarRutas ? rutas : []);
    const gps = coleccionTrayectoria(mostrarRecorrido ? posiciones : []);
    const ultimo = coleccionPosicion(ultima);
    (actual.getSource("rutas-autorizadas") as GeoJSONSource).setData(trazado);
    (actual.getSource("trayectoria-gps") as GeoJSONSource).setData(gps);
    (actual.getSource("ultima-gps") as GeoJSONSource).setData(ultimo);
    if (!primeraVista.current) {
      const bounds = new LngLatBounds();
      const puntos: Coordenada[] = [
        ...rutas.flatMap((r) => coordenadasRuta(r) ?? []),
        ...posiciones.map((p): Coordenada => [p.longitud, p.latitud]),
      ];
      if (ultima) puntos.push([ultima.longitud, ultima.latitud]);
      for (const p of puntos) bounds.extend(p);
      if (puntos.length > 0) {
        actual.fitBounds(bounds, { padding: 48, maxZoom: 13, duration: 0 });
        primeraVista.current = true;
      }
    }
  }, [listo, rutas, posiciones, ultima, mostrarRutas, mostrarRecorrido]);

  useEffect(() => {
    if (centrar > 0 && ultima && mapa.current && listo) {
      mapa.current.flyTo({ center: [ultima.longitud, ultima.latitud], zoom: 13 });
    }
    // centrar es un contador de interacción; no recentrar al llegar nuevas posiciones automáticamente.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [centrar]);

  return (
    <div className="relative">
      <div ref={contenedor} className="h-[370px] w-full overflow-hidden rounded-xl border border-black/10 dark:border-white/10 sm:h-[480px]" aria-label="Mapa con las rutas autorizadas y posiciones GPS" role="img" />
      {error && (
        <p role="alert" className="mt-2 text-sm text-amber-800 dark:text-amber-300">
          No se pudo cargar completamente el mapa base. Revisá la conectividad con el proveedor de mapas.
        </p>
      )}
    </div>
  );
}

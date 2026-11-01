"use client";

import { useEffect, useRef, useState } from "react";
import { Map as MapaGL, NavigationControl, LngLatBounds, setWorkerUrl, type GeoJSONSource } from "maplibre-gl";
import type { FeatureCollection, LineString, Point } from "geojson";
import "maplibre-gl/dist/maplibre-gl.css";
import { coordenadasRuta, type PuntoGpsSeguimiento, type RutaAutorizada } from "@/lib/seguimiento";
import type { PruebaDesvioVista } from "@/lib/casos";

setWorkerUrl(new URL("maplibre-gl/dist/maplibre-gl-worker.mjs", import.meta.url).toString());
const ESTILO = "https://tiles.openfreemap.org/styles/liberty";
const VACIO: FeatureCollection = { type: "FeatureCollection", features: [] };
const COLORES_RUTA = ["#16a34a", "#2563eb", "#a855f7"];

export default function MapaEvidencia({ rutas, puntos, pruebas }: {
  rutas: RutaAutorizada[];
  puntos: PuntoGpsSeguimiento[];
  pruebas: PruebaDesvioVista[];
}) {
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
      for (const id of ["rutas", "trayectoria", "confirmaciones"]) {
        m.addSource(id, { type: "geojson", data: VACIO });
      }
      m.addLayer({ id: "corredores", type: "line", source: "rutas",
        paint: { "line-color": ["get", "color"], "line-width": 5, "line-opacity": 0.85 } });
      m.addLayer({ id: "circulacion-gps", type: "line", source: "trayectoria",
        paint: { "line-color": "#ea580c", "line-width": 4 } });
      m.addLayer({ id: "puntos-confirmacion", type: "circle", source: "confirmaciones",
        paint: { "circle-radius": 8, "circle-color": ["get", "color"],
          "circle-stroke-width": 2, "circle-stroke-color": "#ffffff" } });
      setListo(true);
    });
    m.on("error", () => setError(true));
    return () => { mapa.current = null; m.remove(); };
  }, []);

  useEffect(() => {
    const m = mapa.current;
    if (!m || !listo) return;
    const rutasValidas = rutas.flatMap((r, i) => {
      const coords = coordenadasRuta(r);
      return coords ? [{ type: "Feature" as const, properties: { color: COLORES_RUTA[i % 3] },
        geometry: { type: "LineString" as const, coordinates: coords } }] : [];
    });
    const rutasFc: FeatureCollection<LineString> = { type: "FeatureCollection", features: rutasValidas };
    const coordsGps = puntos.filter(p => Number.isFinite(p.longitud) && Number.isFinite(p.latitud))
      .map(p => [p.longitud, p.latitud]);
    const gpsFc: FeatureCollection<LineString> = { type: "FeatureCollection", features: coordsGps.length >= 2
      ? [{ type: "Feature", properties: {}, geometry: { type: "LineString", coordinates: coordsGps } }] : [] };
    const marcas: FeatureCollection<Point> = { type: "FeatureCollection", features: [
      ...pruebas.flatMap(p => [
        { coord: [p.lonPareja, p.latPareja], color: "#f97316" },
        { coord: [p.lonConfirmante, p.latConfirmante], color: "#dc2626" },
      ]),
      ...(!pruebas.length && puntos.length > 0 ? [
        { coord: [puntos[0].longitud, puntos[0].latitud], color: "#16a34a" },
        { coord: [puntos[puntos.length - 1].longitud, puntos[puntos.length - 1].latitud], color: "#dc2626" },
      ] : []),
    ].filter(p => p.coord.every(Number.isFinite)).map(p => ({
      type: "Feature", properties: { color: p.color },
      geometry: { type: "Point", coordinates: p.coord },
    })) };
    (m.getSource("rutas") as GeoJSONSource).setData(rutasFc);
    (m.getSource("trayectoria") as GeoJSONSource).setData(gpsFc);
    (m.getSource("confirmaciones") as GeoJSONSource).setData(marcas);
    const bounds = new LngLatBounds();
    const coord = [
      ...rutasValidas.flatMap(f => f.geometry.coordinates),
      ...coordsGps,
      ...marcas.features.map(f => f.geometry.coordinates),
    ];
    coord.forEach(c => bounds.extend([c[0], c[1]]));
    if (coord.length) m.fitBounds(bounds, { padding: 48, maxZoom: 13, duration: 0 });
  }, [listo, rutas, puntos, pruebas]);

  return <div>
    <div ref={nodo} role="img" aria-label="Mapa de evidencias del caso" className="h-[370px] w-full rounded-xl border border-black/10 dark:border-white/10 sm:h-[480px]" />
    {error && <p role="alert" className="mt-2 text-xs text-amber-700 dark:text-amber-300">
      El mapa base podría no estar disponible. Revisá la conexión con el proveedor cartográfico.
    </p>}
  </div>;
}

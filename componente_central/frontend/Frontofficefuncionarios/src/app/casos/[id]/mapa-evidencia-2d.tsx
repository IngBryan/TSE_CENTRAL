"use client";

import { useEffect, useRef, useState } from "react";
import * as L from "leaflet";
import "leaflet/dist/leaflet.css";
import { coordenadasRuta, type Coordenada } from "@/lib/seguimiento";
import type { MapaEvidenciaProps } from "./mapa-evidencia";

const COLORES_RUTA = ["#16a34a", "#2563eb", "#a855f7"];
const valida = (c: Coordenada) => Number.isFinite(c[0]) && Number.isFinite(c[1])
  && Math.abs(c[0]) <= 180 && Math.abs(c[1]) <= 90;
const latLng = (c: Coordenada): L.LatLngTuple => [c[1], c[0]];

/** Mismas evidencias en un mapa 2D para navegadores sin WebGL. */
export default function MapaEvidencia2D({ rutas, puntos, pruebas, mostrarRutas = true,
  mostrarRecorrido = true, enfocar = 0, modoEncuadre = "todo", contexto = "seguimiento" }: MapaEvidenciaProps) {
  const nodo = useRef<HTMLDivElement | null>(null);
  const mapa = useRef<L.Map | null>(null);
  const capas = useRef<L.LayerGroup | null>(null);
  const vistaInicial = useRef(false);
  const [errorBase, setErrorBase] = useState(false);

  useEffect(() => {
    if (!nodo.current) return;
    const m = L.map(nodo.current, { zoomControl: false }).setView([-33.5, -56.2], 6);
    mapa.current = m;
    L.control.zoom({ position: "topright" }).addTo(m);
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    }).on("tileerror", () => setErrorBase(true)).addTo(m);
    capas.current = L.layerGroup().addTo(m);
    const observer = new ResizeObserver(() => m.invalidateSize());
    observer.observe(nodo.current);
    return () => { observer.disconnect(); capas.current = null; mapa.current = null; m.remove(); };
  }, []);

  useEffect(() => {
    const m = mapa.current, grupo = capas.current;
    if (!m || !grupo) return;
    grupo.clearLayers();
    const rutasValidas = rutas.flatMap((r, i) => {
      const coords = coordenadasRuta(r)?.filter(valida);
      return coords?.length ? [{ coords, color: COLORES_RUTA[i % COLORES_RUTA.length] }] : [];
    });
    if (mostrarRutas) for (const ruta of rutasValidas) {
      L.polyline(ruta.coords.map(latLng), { color: ruta.color, weight: 5, opacity: 0.8 }).addTo(grupo);
    }
    const gps = puntos.map(p => [p.longitud, p.latitud] as Coordenada).filter(valida);
    if (mostrarRecorrido && gps.length >= 2) {
      L.polyline(gps.map(latLng), { color: "#ea580c", weight: 4, dashArray: "8 4" }).addTo(grupo);
    }
    const seleccion = pruebas.flatMap(p => [
      { coord: [p.lonPareja, p.latPareja] as Coordenada, color: "#eab308" },
      { coord: [p.lonConfirmante, p.latConfirmante] as Coordenada, color: "#dc2626" },
    ]).filter(p => valida(p.coord));
    const marcas = seleccion.length ? seleccion : gps.length ? [
      { coord: gps[0], color: "#16a34a" },
      { coord: gps[gps.length - 1], color: "#dc2626" },
    ] : [];
    for (const marca of marcas) {
      L.circleMarker(latLng(marca.coord), {
        radius: 8, color: "#ffffff", weight: 2, fillColor: marca.color, fillOpacity: 1,
      }).addTo(grupo);
    }
    const todas = [...rutasValidas.flatMap(r => r.coords), ...gps, ...marcas.map(p => p.coord)];
    const foco = modoEncuadre === "confirmacion" && seleccion.length ? seleccion.map(p => p.coord) : todas;
    if (foco.length && (!vistaInicial.current || enfocar > 0)) {
      m.fitBounds(L.latLngBounds(foco.map(latLng)), { padding: [48, 48], maxZoom: 16, animate: false });
      vistaInicial.current = true;
    }
  }, [rutas, puntos, pruebas, mostrarRutas, mostrarRecorrido, enfocar, modoEncuadre]);

  return <div>
    <div ref={nodo} role="img" aria-label={contexto === "evidencia"
      ? "Evidencia registrada: dos mediciones del desvío y geometrías de referencia archivadas"
      : contexto === "sin-guia" ? "Posiciones vinculadas a la circulación sin guía"
      : "Recorrido GPS posterior a la detección y rutas actualmente autorizadas"}
      className="relative z-0 h-[370px] w-full rounded-xl border border-black/10 dark:border-white/10 sm:h-[480px]" />
    {errorBase && <p role="alert" className="mt-2 text-xs text-amber-700 dark:text-amber-300">
      El mapa base podría no estar disponible. Revisá la conexión con el proveedor cartográfico.
    </p>}
  </div>;
}

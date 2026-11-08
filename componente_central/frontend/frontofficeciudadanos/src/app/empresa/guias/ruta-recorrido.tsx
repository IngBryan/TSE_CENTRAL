"use client";

import dynamic from "next/dynamic";
import { useEffect, useState } from "react";
import { previsualizarRutas } from "./[id]/rutas/actions";
import type { Previsualizacion } from "./[id]/rutas/tipos-rutas";
import { botonSecundario } from "@/components/estilos";

const MapaRutas = dynamic(() => import("./[id]/rutas/mapa-rutas"), { ssr: false });

// El padre monta una instancia nueva al cambiar el par de localidades.
export function RutaRecorrido({ origenId, destinoId, onChange }: {
  origenId: number; destinoId: number; onChange: (ruta: Previsualizacion | null) => void;
}) {
  const [ruta, setRuta] = useState<Previsualizacion | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [intento, setIntento] = useState(0);

  useEffect(() => {
    let vigente = true;
    previsualizarRutas(origenId, destinoId).then(res => {
      if (!vigente) return;
      if (res.ok) { setRuta(res.datos); onChange(res.datos); }
      else setError(res.error);
    }).catch(() => {
      if (vigente) setError("No se pudo calcular la ruta. Volvé a intentarlo.");
    });
    return () => { vigente = false; };
  }, [origenId, destinoId, onChange, intento]);

  return <section className="flex flex-col gap-3" aria-label="Ruta admitida">
    <h2 className="text-lg font-semibold">Ruta admitida</h2>
    <p className="text-sm text-zinc-600 dark:text-zinc-400">Se admite únicamente la ruta de menor duración.</p>
    {error ? <>
      <p role="alert" className="text-sm text-red-700 dark:text-red-300">{error}</p>
      <button type="button" className={`${botonSecundario} self-start`} onClick={() => {
        setError(null); setRuta(null); onChange(null); setIntento(i => i + 1);
      }}>Reintentar cálculo</button>
    </> : !ruta ? <p role="status">Calculando ruta…</p> : <>
      <p className="text-sm">
        {(ruta.rutas[0].distanciaMetros / 1000).toLocaleString("es-UY", { maximumFractionDigits: 1 })} km
        {" · "}{Math.round(ruta.rutas[0].duracionSegundos / 60)} min aprox.
      </p>
      <MapaRutas rutas={ruta.rutas} />
      <button type="button" className={`${botonSecundario} self-start`} onClick={() => {
        setRuta(null); onChange(null); setIntento(i => i + 1);
      }}>Recalcular ruta</button>
    </>}
  </section>;
}

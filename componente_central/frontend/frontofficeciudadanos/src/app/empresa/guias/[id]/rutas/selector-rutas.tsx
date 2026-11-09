"use client";

import dynamic from "next/dynamic";
import { useState, useTransition } from "react";
import type { Guia } from "../../actions";
import { confirmarRutas } from "./actions";
import { localidadDeGuia, type LocalidadVista } from "@/lib/localidades";
import type { Previsualizacion, RutaAsignada } from "./tipos-rutas";
import { RutaRecorrido } from "../../ruta-recorrido";
import { botonGubUy, botonSecundario } from "@/components/estilos";

const MapaRutas = dynamic(() => import("./mapa-rutas"), { ssr: false });

export function SelectorRutas({ guia, localidades, iniciales }: {
  guia: Guia; localidades: LocalidadVista[]; iniciales: RutaAsignada[];
}) {
  const origen = localidadDeGuia(localidades, guia.origenId, guia.origen);
  const destino = localidadDeGuia(localidades, guia.destinoId, guia.destino);
  const [asignadas, setAsignadas] = useState(iniciales.slice(0, 1));
  const [calculando, setCalculando] = useState(false);
  const [preview, setPreview] = useState<Previsualizacion | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [exito, setExito] = useState(false);
  const [ocupado, iniciar] = useTransition();
  const editable = guia.estado === "SIN_ASIGNAR" || guia.estado === "ASIGNADA";

  return <section className="flex flex-col gap-4 rounded-2xl bg-white p-5 shadow-sm dark:bg-zinc-950">
    {error && <p role="alert" className="text-red-700 dark:text-red-300">{error}</p>}
    {exito && <p role="status" className="text-green-800 dark:text-green-200">Ruta admitida guardada.</p>}
    {calculando && origen && destino ? <>
      <RutaRecorrido key={`${origen.id}:${destino.id}`} origenId={origen.id} destinoId={destino.id} onChange={setPreview} />
      <button type="button" disabled={!preview || ocupado} className={`${botonGubUy} self-start`} onClick={() => {
        if (!preview) return;
        iniciar(async () => {
          setError(null);
          const res = await confirmarRutas(guia.id, origen.id, destino.id, preview.previsualizacionId);
          if (!res.ok) { setError(res.error); setPreview(null); setCalculando(false); return; }
          setAsignadas(res.datos); setPreview(null); setCalculando(false); setExito(true);
        });
      }}>{ocupado ? "Guardando…" : "Confirmar ruta admitida"}</button>
      <button type="button" disabled={ocupado} className={`${botonSecundario} self-start`}
        onClick={() => { setCalculando(false); setPreview(null); }}>Cancelar</button>
    </> : <>
      {asignadas.length ? <>
        <p>{(asignadas[0].distanciaMetros / 1000).toLocaleString("es-UY", { maximumFractionDigits: 1 })} km</p>
        <MapaRutas rutas={asignadas} />
      </> : <p>Esta guía todavía no tiene ruta admitida.</p>}
      {editable && origen && destino && origen.id !== destino.id && <button type="button"
        className={`${botonSecundario} self-start`} onClick={() => {
          setError(null); setExito(false); setCalculando(true);
        }}>{asignadas.length ? "Recalcular ruta admitida" : "Calcular ruta admitida"}</button>}
      {editable && (!origen || !destino) && <p className="text-sm text-amber-800 dark:text-amber-200">
        Editá la guía para seleccionar su origen y destino del catálogo.
      </p>}
    </>}
  </section>;
}

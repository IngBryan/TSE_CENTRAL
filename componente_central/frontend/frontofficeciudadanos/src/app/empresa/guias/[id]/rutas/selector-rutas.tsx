"use client";

import dynamic from "next/dynamic";
import { useState, useTransition } from "react";
import type { Guia } from "../../actions";
import { confirmarRutas, previsualizarRutas } from "./actions";
import { localidadDeGuia, mostrarLocalidad, type LocalidadVista } from "@/lib/localidades";
import type { Previsualizacion, RutaAsignada } from "./tipos-rutas";

const MapaRutas = dynamic(() => import("./mapa-rutas"), { ssr: false });
const COLORES = ["#16a34a", "#2563eb", "#a855f7"];
const km = (metros: number) => `${(metros / 1000).toLocaleString("es-UY", { maximumFractionDigits: 1 })} km`;
const minutos = (segundos: number) => `${Math.round(segundos / 60)} min aprox.`;

export function SelectorRutas({ guia, localidades, iniciales, seleccion }: {
  guia: Guia;
  localidades: LocalidadVista[];
  iniciales: RutaAsignada[];
  seleccion?: { valor: Previsualizacion | null; onChange: (valor: Previsualizacion | null) => void };
}) {
  const origenPorGuia = localidadDeGuia(localidades, guia.origenId, guia.origen);
  const destinoPorGuia = localidadDeGuia(localidades, guia.destinoId, guia.destino);
  const [origenId, setOrigenId] = useState<number>(origenPorGuia?.id ?? 0);
  const [destinoId, setDestinoId] = useState<number>(destinoPorGuia?.id ?? 0);
  const [previewLocal, setPreviewLocal] = useState<Previsualizacion | null>(null);
  const preview = seleccion ? seleccion.valor : previewLocal;
  const setPreview = (valor: Previsualizacion | null) => {
    if (seleccion) seleccion.onChange(valor);
    else setPreviewLocal(valor);
  };
  const [asignadas, setAsignadas] = useState(iniciales);
  const [visible, setVisible] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [exito, setExito] = useState<string | null>(null);
  const [ocupado, iniciar] = useTransition();
  const editable = guia.estado === "SIN_ASIGNAR" || guia.estado === "ASIGNADA";
  const coinciden = origenId === origenPorGuia?.id && destinoId === destinoPorGuia?.id;
  const mismasLocalidades = origenId > 0 && origenId === destinoId;
  const seleccionValida = origenId > 0 && destinoId > 0 && !mismasLocalidades;
  const rutasVisibles = preview?.rutas ?? asignadas;
  const cambiar = (tipo: "origen" | "destino", valor: number) => {
    if (tipo === "origen") setOrigenId(valor); else setDestinoId(valor);
    setPreview(null); setError(null); setExito(null); setVisible(null);
  };
  const previsualizar = () => iniciar(async () => {
    setError(null); setExito(null);
    setPreview(null);
    const res = await previsualizarRutas(origenId, destinoId);
    if (!res.ok) { setError(res.error); return; }
    setPreview(res.datos); setVisible(null);
  });
  const confirmar = () => {
    if (!preview || !editable || !coinciden) return;
    iniciar(async () => {
      setError(null); setExito(null);
      const res = await confirmarRutas(guia.id, origenId, destinoId, preview.previsualizacionId);
      if (!res.ok) { setError(res.error); setPreview(null); return; }
      setAsignadas(res.datos); setPreview(null); setVisible(null);
      setExito(`${res.datos.length} ruta(s) autorizada(s) asociada(s) a la guía ${guia.id}.`);
    });
  };
  return <div className="flex flex-col gap-5">
    <section className="rounded-2xl bg-white p-5 text-sm shadow-sm dark:bg-zinc-950">
      <h2 className="mb-2 text-lg font-semibold">{seleccion ? "Localidades del recorrido" : "1. Seleccionar localidades"}</h2>
      <p className="mb-4 text-zinc-600 dark:text-zinc-400">
        Recorrido registrado en la guía: <b>{mostrarLocalidad(guia.origen, guia.origenDepartamento)} → {mostrarLocalidad(guia.destino, guia.destinoDepartamento)}</b>.
        Las localidades elegidas deben coincidir con esos datos para poder confirmar rutas.
      </p>
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="flex flex-col gap-1">Origen
          <select className="rounded-lg border border-zinc-300 bg-white p-2 dark:border-zinc-700 dark:bg-zinc-950"
            value={origenId} disabled={ocupado || !editable || !!seleccion} onChange={e => cambiar("origen", Number(e.target.value))}>
            <option value={0}>Seleccionar localidad…</option>
            {localidades.map(l => <option key={l.id} value={l.id}>{l.nombre} ({l.departamento})</option>)}
          </select>
        </label>
        <label className="flex flex-col gap-1">Destino
          <select className="rounded-lg border border-zinc-300 bg-white p-2 dark:border-zinc-700 dark:bg-zinc-950"
            value={destinoId} disabled={ocupado || !editable || !!seleccion} onChange={e => cambiar("destino", Number(e.target.value))}>
            <option value={0}>Seleccionar localidad…</option>
            {localidades.map(l => <option key={l.id} value={l.id}>{l.nombre} ({l.departamento})</option>)}
          </select>
        </label>
      </div>
      {!editable && <p role="status" className="mt-3 rounded-lg bg-amber-50 p-3 text-amber-900 dark:bg-amber-950 dark:text-amber-200">
        La guía está {guia.estado.toLowerCase().replaceAll("_", " ")}. Las rutas ya no pueden modificarse.
      </p>}
      {editable && (!origenPorGuia || !destinoPorGuia) && <p role="status" className="mt-3 text-amber-800 dark:text-amber-200">
        El origen o destino de esta guía no existe en el catálogo de localidades. No se puede confirmar una ruta
        sin que coincidan los datos de la guía. No se modificará la guía desde esta pantalla.
      </p>}
      {editable && mismasLocalidades && <p role="status" id="aviso-origen-destino-iguales"
        className="mt-3 text-amber-800 dark:text-amber-200">
        Elegiste la misma localidad como origen y destino. Para calcular un recorrido, seleccioná dos localidades diferentes.
      </p>}
      {editable && seleccionValida && !coinciden && <p role="status" className="mt-3 text-amber-800 dark:text-amber-200">
        Estas localidades no coinciden con el recorrido actual de la guía. Podés previsualizarlo,
        pero no vas a poder autorizarlo para esta guía hasta que coincidan.
      </p>}
      <button type="button" disabled={!editable || ocupado || !seleccionValida}
        aria-describedby={mismasLocalidades ? "aviso-origen-destino-iguales" : undefined}
        onClick={previsualizar} className="mt-4 rounded-lg bg-[#0068b3] px-4 py-2 font-medium text-white disabled:opacity-40">
        {ocupado ? "Buscando rutas…" : "Previsualizar alternativas"}
      </button>
    </section>
    {error && <p role="alert" className="rounded-xl bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">{error}</p>}
    {exito && <p role="status" className="rounded-xl bg-green-50 p-4 text-green-800 dark:bg-green-950 dark:text-green-200">{exito}</p>}
    <section className="rounded-2xl bg-white p-5 text-sm shadow-sm dark:bg-zinc-950">
      <h2 className="mb-2 text-lg font-semibold">{seleccion ? "" : "2. "}{preview ? "Alternativas calculadas" : "Rutas autorizadas de la guía"}</h2>
      {rutasVisibles.length === 0 ? <p className="text-zinc-500">Todavía no hay rutas para mostrar. Elegí localidades y previsualizá.</p> : <>
        <div className="mb-3 flex flex-wrap gap-2">
          <button type="button" aria-pressed={visible === null} onClick={() => setVisible(null)}
            className={`rounded-lg border px-3 py-1.5 text-xs ${visible === null ? "border-sky-600 font-semibold" : ""}`}>
            Todas las rutas
          </button>
          {rutasVisibles.map((ruta) => <button type="button" key={ruta.alternativa}
            aria-pressed={visible === ruta.alternativa} onClick={() => setVisible(ruta.alternativa)}
            className={`rounded-lg border px-3 py-1.5 text-xs ${visible === ruta.alternativa ? "border-sky-600 font-semibold" : ""}`}>
            <span style={{ color: COLORES[(ruta.alternativa - 1) % COLORES.length] }}>━━</span> Alternativa {ruta.alternativa}
          </button>)}
        </div>
        <MapaRutas rutas={rutasVisibles} visible={visible} />
        <div className="mt-3 grid gap-2 sm:grid-cols-2">
          {rutasVisibles.map(ruta => <p key={ruta.alternativa} className="rounded-lg bg-zinc-50 p-3 dark:bg-zinc-900">
            <b>Alternativa {ruta.alternativa}</b>: {km(ruta.distanciaMetros)}
            {"duracionSegundos" in ruta && <span> · {minutos(ruta.duracionSegundos)}</span>}
          </p>)}
        </div>
      </>}
      {preview && <div className="mt-4 border-t border-zinc-200 pt-4 dark:border-zinc-800">
        <p className="mb-3 text-zinc-600 dark:text-zinc-400">
          Encontramos {preview.rutas.length} alternativa(s) que cumplen las condiciones de recorrido.
          {seleccion ? "Se guardarán junto con el vehículo y el chofer al confirmar la asignación en el siguiente paso."
            : "Al confirmar, se guardarán las rutas mostradas para esta guía."}
        </p>
        {!seleccion && <button type="button" disabled={ocupado || !coinciden || !editable} onClick={confirmar}
          className="rounded-lg bg-green-700 px-4 py-2 font-medium text-white disabled:opacity-40">
          {ocupado ? "Confirmando…" : "Confirmar rutas para esta guía"}
        </button>}
      </div>}
    </section>
  </div>;
}

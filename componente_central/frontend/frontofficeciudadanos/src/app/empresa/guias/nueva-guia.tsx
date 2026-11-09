"use client";

import { useActionState, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { RutaRecorrido } from "./ruta-recorrido";
import type { Previsualizacion } from "./[id]/rutas/tipos-rutas";
import { botonGubUy, botonSecundario } from "@/components/estilos";
import { SelectorLocalidad } from "@/components/selector-localidad";
import type { LocalidadVista } from "@/lib/localidades";
import { crearGuia, type EstadoNuevaGuia, type Rubro, type TipoCarga } from "./actions";
import { OpcionesCatalogo } from "./opciones-catalogo";

// rubroCliente y tipoCarga son listas: los catalogos precargados de rubros y de tipos de carga
const CAMPOS = [
  { nombre: "rubroCliente", etiqueta: "Rubro del cliente", tipo: "select", vacio: "Elegí un rubro", plural: "rubros" },
  { nombre: "tipoCarga", etiqueta: "Tipo de carga", tipo: "select", vacio: "Elegí un tipo de carga", plural: "tipos de carga" },
  { nombre: "pesoCargaKg", etiqueta: "Carga (kg)", tipo: "number" },
  { nombre: "fecha", etiqueta: "Fecha del viaje", tipo: "date" },
] as const;

export function NuevaGuia({
  hoy,
  tiposCarga,
  rubros,
  localidades,
}: {
  hoy: string;
  tiposCarga: TipoCarga[];
  rubros: Rubro[];
  localidades: LocalidadVista[];
}) {
  const opciones = { rubroCliente: rubros, tipoCarga: tiposCarga };
  const router = useRouter();
  const [ruta, setRuta] = useState<Previsualizacion | null>(null);
  const [origenId, setOrigenId] = useState<number | null>(null);
  const [destinoId, setDestinoId] = useState<number | null>(null);
  const [estado, accion, guardando] = useActionState<EstadoNuevaGuia, FormData>(async (previo, datos) => {
    const r = await crearGuia(previo, datos);
    if (r.ok) router.push(`/empresa/guias?creada=${encodeURIComponent(r.ok)}`);
    return r;
  }, {});

  return (
    <form action={accion} className="flex flex-col gap-4 rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950">
      {estado.error && (
        <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
          {estado.error}
        </p>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        {CAMPOS.map((c) => {
          const error = estado.campos?.[c.nombre];
          return (
            <label key={c.nombre} className="flex flex-col gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300">
              {c.etiqueta}
              {c.tipo === "select" ? (
                <select
                  name={c.nombre}
                  // key: al volver con un error se re-monta con el valor elegido
                  key={estado.valores?.[c.nombre] ?? ""}
                  defaultValue={estado.valores?.[c.nombre] ?? ""}
                  required
                  aria-invalid={error ? true : undefined}
                  className={`h-10 rounded-lg border bg-transparent px-3 text-base font-normal text-black dark:bg-zinc-950 dark:text-zinc-50 ${
                    error ? "border-red-500" : "border-black/15 dark:border-white/20"
                  }`}
                >
                  <OpcionesCatalogo items={opciones[c.nombre]} vacio={c.vacio} />
                </select>
              ) : (
                <input
                  name={c.nombre}
                  type={c.tipo}
                  defaultValue={estado.valores?.[c.nombre] ?? ""}
                  required
                  min={c.tipo === "number" ? 1 : c.tipo === "date" ? hoy : undefined}
                  step={c.tipo === "number" ? 1 : undefined}
                  aria-invalid={error ? true : undefined}
                  className={`h-10 rounded-lg border bg-transparent px-3 text-base font-normal text-black dark:text-zinc-50 ${
                    error ? "border-red-500" : "border-black/15 dark:border-white/20"
                  }`}
                />
              )}
              {error ? (
                <span className="text-xs font-normal text-red-700 dark:text-red-300">{error}</span>
              ) : (
                c.tipo === "select" &&
                opciones[c.nombre].length === 0 && (
                  <span className="text-xs font-normal text-red-700 dark:text-red-300">
                    No se pudieron obtener los {c.plural}. Probá de nuevo en unos minutos.
                  </span>
                )
              )}
            </label>
          );
        })}
        <SelectorLocalidad nombre="origenId" etiqueta="Origen" localidades={localidades}
          valor={origenId ?? ""} onChange={id => { setOrigenId(id); setRuta(null); }}
          error={estado.campos?.origenId || estado.campos?.origen} />
        <SelectorLocalidad nombre="destinoId" etiqueta="Destino" localidades={localidades}
          valor={destinoId ?? ""} onChange={id => { setDestinoId(id); setRuta(null); }}
          error={estado.campos?.destinoId || estado.campos?.destino} />
      </div>
      {origenId !== null && origenId === destinoId && <p role="alert" className="text-sm text-red-700">
        Seleccioná localidades diferentes para origen y destino.
      </p>}
      {origenId && destinoId && origenId !== destinoId && (
        <RutaRecorrido key={`${origenId}:${destinoId}`} origenId={origenId} destinoId={destinoId} onChange={setRuta} />
      )}
      <input type="hidden" name="previsualizacionId" value={ruta?.previsualizacionId ?? ""} />
      {localidades.length === 0 && <p role="alert" className="text-sm text-red-700">
        No se pudo obtener el catálogo de localidades. Recargá la página para crear una guía.
      </p>}
      <div className="flex gap-3">
        <button type="submit" disabled={guardando || !ruta || ruta.origenId !== origenId || ruta.destinoId !== destinoId} className={botonGubUy}>
          {guardando ? "Guardando…" : "Guardar guía"}
        </button>
        <Link href="/empresa/guias" className={botonSecundario}>Cancelar</Link>
      </div>
    </form>
  );
}

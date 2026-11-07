"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { botonGubUy, botonSecundario } from "@/components/estilos";
import { SelectorLocalidad } from "@/components/selector-localidad";
import { localidadDeGuia, type LocalidadVista } from "@/lib/localidades";
import {
  cancelarGuia,
  editarGuia,
  reasignarGuia,
  type ChoferResumen,
  type Guia,
  type ResultadoModificacion,
  type Rubro,
  type TipoCarga,
  type VehiculoResumen,
} from "../actions";
import { OpcionesCatalogo } from "../opciones-catalogo";

type Modo = "editar" | "reasignar" | "cancelar" | null;

const nombre = (c: ChoferResumen) => [c.primerNombre, c.primerApellido].filter(Boolean).join(" ") || c.cedula;

const campo = "h-10 rounded-lg border border-black/15 bg-transparent px-3 text-base font-normal text-black dark:border-white/20 dark:text-zinc-50";
const etiqueta = "flex flex-col gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300";

// Modificacion de una guia ASIGNADA por el responsable: editar datos, reasignar vehiculo/chofer o
// cancelar. Se manda la version de la guia que se esta viendo; si cambio entretanto (p. ej. el chofer
// la inicio) el backend responde 409 y se pide recargar.
export function ModificarGuia({
  guia,
  vehiculos,
  choferes,
  tiposCarga,
  rubros,
  hoy,
  localidades,
}: {
  guia: Guia;
  vehiculos: VehiculoResumen[];
  choferes: ChoferResumen[];
  tiposCarga: TipoCarga[];
  rubros: Rubro[];
  hoy: string;
  localidades: LocalidadVista[];
}) {
  // Opciones de los campos que se eligen de un catalogo
  const opciones: Partial<Record<string, { id: number; nombre: string }[]>> = {
    rubroCliente: rubros,
    tipoCarga: tiposCarga,
  };
  const router = useRouter();
  const [modo, setModo] = useState<Modo>(null);
  const [error, setError] = useState<string | null>(null);
  const [campos, setCampos] = useState<Record<string, string>>({});
  const [confirmar, setConfirmar] = useState(false);
  const [pendiente, iniciar] = useTransition();

  const [datos, setDatos] = useState({
    rubroCliente: guia.rubroCliente,
    tipoCarga: guia.tipoCarga ?? "",
    pesoCargaKg: String(guia.pesoCargaKg),
    fecha: guia.fecha,
    origenId: localidadDeGuia(localidades, guia.origenId, guia.origen)?.id ?? null,
    destinoId: localidadDeGuia(localidades, guia.destinoId, guia.destino)?.id ?? null,
  });
  const [matricula, setMatricula] = useState(guia.vehiculo?.matricula ?? "");
  const [idChofer, setIdChofer] = useState<number | null>(guia.chofer?.id ?? null);
  const [motivo, setMotivo] = useState("");
  const version = guia.version ?? 0;

  const abrir = (m: Modo) => {
    setModo(m);
    setError(null);
    setCampos({});
    setConfirmar(false);
  };

  const enviar = (accion: () => Promise<ResultadoModificacion>) => {
    setError(null);
    iniciar(async () => {
      const r = await accion();
      if (r.ok) {
        abrir(null);
        router.refresh();
      } else {
        setError(r.error);
        setCampos(r.campos ?? {});
        setConfirmar(false);
      }
    });
  };

  const guardar = () => {
    if (modo === "editar") {
      const kg = Number(datos.pesoCargaKg);
      enviar(() =>
        editarGuia(guia.id, version, { ...datos, pesoCargaKg: datos.pesoCargaKg === "" || !Number.isFinite(kg) ? null : Math.trunc(kg) }),
      );
    } else if (modo === "reasignar" && idChofer != null) {
      enviar(() => reasignarGuia(guia.id, version, matricula, idChofer));
    } else if (modo === "cancelar") {
      enviar(() => cancelarGuia(guia.id, version, motivo));
    }
  };

  const errorCampo = (n: string) => campos[n] && <span className="text-xs font-normal text-red-700 dark:text-red-300">{campos[n]}</span>;

  return (
    <section className="flex flex-col gap-4 rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950">
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="mr-auto text-lg font-semibold text-black dark:text-zinc-50">Modificar guía</h2>
        <button type="button" onClick={() => abrir("editar")} className={`${botonSecundario} h-9 px-4`}>
          Editar
        </button>
        <button type="button" onClick={() => abrir("reasignar")} className={`${botonSecundario} h-9 px-4`}>
          Reasignar
        </button>
        <button type="button" onClick={() => abrir("cancelar")} className={`${botonSecundario} h-9 px-4 text-red-700 dark:text-red-300`}>
          Cancelar guía
        </button>
      </div>
      <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900 dark:bg-amber-950 dark:text-amber-200">
        El chofer puede estar sin conexión con la versión anterior; sus eventos se resolverán según la política de conflictos.
      </p>

      {error && (
        <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
          {error}
        </p>
      )}

      {modo === "editar" && (
        <div className="grid gap-4 sm:grid-cols-2">
          {(
            [
              ["rubroCliente", "Rubro del cliente", "select"],
              ["tipoCarga", "Tipo de carga", "select"],
              ["pesoCargaKg", "Carga (kg)", "number"],
              ["fecha", "Fecha del viaje", "date"],
                    ] as const
          ).map(([n, texto, tipo]) => (
            <label key={n} className={etiqueta}>
              {texto}
              {tipo === "select" ? (
                // Rubro del cliente y tipo de carga: de los catalogos precargados
                <select
                  value={datos[n]}
                  onChange={(e) => setDatos({ ...datos, [n]: e.target.value })}
                  className={`${campo} dark:bg-zinc-950`}
                >
                  <OpcionesCatalogo
                    items={opciones[n] ?? []}
                    vacio={n === "rubroCliente" ? "Elegí un rubro" : "Elegí un tipo de carga"}
                    actual={datos[n]}
                  />
                </select>
              ) : (
                <input
                  type={tipo}
                  value={datos[n]}
                  min={tipo === "date" ? hoy : tipo === "number" ? 1 : undefined}
                  onChange={(e) => setDatos({ ...datos, [n]: e.target.value })}
                  className={campo}
                />
              )}
              {errorCampo(n)}
            </label>
          ))}
          <SelectorLocalidad nombre="origenId" etiqueta="Origen" localidades={localidades}
            valor={datos.origenId} onChange={id => setDatos({ ...datos, origenId: id })}
            error={campos.origenId || campos.origen} />
          <SelectorLocalidad nombre="destinoId" etiqueta="Destino" localidades={localidades}
            valor={datos.destinoId} onChange={id => setDatos({ ...datos, destinoId: id })}
            error={campos.destinoId || campos.destino} />
          {(datos.origenId === null || datos.destinoId === null) && (
            <p className="sm:col-span-2 text-sm text-amber-800" role="status">
              Esta guía puede ser anterior al catálogo o tener localidades ambiguas. Elegí su origen y destino.
              Valores anteriores: {guia.origen} → {guia.destino}.
            </p>
          )}
        </div>
      )}

      {modo === "reasignar" && (
        <div className="grid gap-4 sm:grid-cols-2">
          <label className={etiqueta}>
            Vehículo
            <select value={matricula} onChange={(e) => setMatricula(e.target.value)} className={campo}>
              {vehiculos.map((v) => (
                <option key={v.matricula} value={v.matricula}>
                  {v.matricula} · {v.marca} {v.modelo}
                </option>
              ))}
            </select>
            {errorCampo("vehiculo")}
          </label>
          <label className={etiqueta}>
            Chofer
            <select value={idChofer ?? ""} onChange={(e) => setIdChofer(Number(e.target.value))} className={campo}>
              {choferes.map((c) => (
                <option key={c.id} value={c.id}>
                  {nombre(c)} (C.I. {c.cedula})
                </option>
              ))}
            </select>
            {errorCampo("chofer")}
          </label>
        </div>
      )}

      {modo === "cancelar" && (
        <label className={etiqueta}>
          Motivo de la cancelación (obligatorio)
          <textarea
            value={motivo}
            maxLength={500}
            rows={3}
            onChange={(e) => setMotivo(e.target.value)}
            className="rounded-lg border border-black/15 bg-transparent p-3 text-base font-normal text-black dark:border-white/20 dark:text-zinc-50"
          />
          {errorCampo("motivo")}
        </label>
      )}

      {modo && !confirmar && (
        <div className="flex gap-3">
          <button
            type="button"
            disabled={pendiente || (modo === "cancelar" && motivo.trim() === "") ||
              (modo === "editar" && (datos.origenId === null || datos.destinoId === null || datos.origenId === datos.destinoId))}
            onClick={() => setConfirmar(true)}
            className={botonGubUy}
          >
            {modo === "editar" ? "Guardar cambios" : modo === "reasignar" ? "Reasignar" : "Cancelar la guía"}
          </button>
          <button type="button" onClick={() => abrir(null)} className={botonSecundario}>
            Volver
          </button>
        </div>
      )}

      {modo && confirmar && (
        <div className="flex flex-col gap-3 rounded-lg border border-black/10 p-4 dark:border-white/15">
          <p className="text-sm text-zinc-700 dark:text-zinc-300">
            {modo === "editar" && "¿Guardar los cambios de la guía? El chofer los verá al sincronizar."}
            {modo === "reasignar" &&
              "¿Reasignar la guía? Si el chofer anterior registra eventos sin conexión, quedarán en conflicto."}
            {modo === "cancelar" &&
              "¿Cancelar la guía? No se puede deshacer; desaparece de la app del chofer y sus eventos posteriores quedarán en conflicto."}
          </p>
          <div className="flex gap-3">
            <button type="button" disabled={pendiente} onClick={guardar} className={botonGubUy}>
              {pendiente ? "Guardando…" : "Confirmar"}
            </button>
            <button type="button" disabled={pendiente} onClick={() => setConfirmar(false)} className={botonSecundario}>
              No
            </button>
          </div>
        </div>
      )}
    </section>
  );
}

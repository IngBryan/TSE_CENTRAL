"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { botonGubUy, botonSecundario } from "@/components/estilos";
import { agregarItv, darDeBaja, editarVehiculo, registrarPermiso, type Resultado, type Vehiculo } from "../actions";

type Modo = "editar" | "permiso" | "itv" | "baja" | null;

const campo = "h-10 rounded-lg border border-black/15 bg-transparent px-3 text-base font-normal text-black dark:border-white/20 dark:text-zinc-50";
const etiqueta = "flex flex-col gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300";

// Acciones sobre un vehiculo activo: editar sus datos (no la matricula), registrar o reemplazar el
// permiso de circulacion, agregar una ITV y darlo de baja (logica, con confirmacion).
export function GestionarVehiculo({ vehiculo }: { vehiculo: Vehiculo }) {
  const router = useRouter();
  const [modo, setModo] = useState<Modo>(null);
  const [error, setError] = useState<string | null>(null);
  const [ok, setOk] = useState<string | null>(null);
  const [campos, setCampos] = useState<Record<string, string>>({});
  const [pendiente, iniciar] = useTransition();

  const [datos, setDatos] = useState({
    marca: vehiculo.marca,
    modelo: vehiculo.modelo,
    peso: String(vehiculo.peso),
    capacidadCargaKg: String(vehiculo.capacidadCargaKg),
  });
  const [permiso, setPermiso] = useState({
    nro: vehiculo.permiso?.nro ?? "",
    fechaEmision: vehiculo.permiso?.fechaEmision ?? "",
    fechaVencimiento: vehiculo.permiso?.fechaVencimiento ?? "",
  });
  const [itv, setItv] = useState("");

  const abrir = (m: Modo) => {
    setModo(m);
    setError(null);
    setOk(null);
    setCampos({});
  };

  const enviar = (accion: () => Promise<Resultado>, mensaje: string) => {
    setError(null);
    iniciar(async () => {
      const r = await accion();
      if (r.ok) {
        abrir(null);
        setOk(mensaje);
        setItv("");
        router.refresh();
      } else {
        setError(r.error);
        setCampos(r.campos ?? {});
      }
    });
  };

  const guardar = () => {
    if (modo === "editar") enviar(() => editarVehiculo(vehiculo.matricula, datos), "Guardamos los cambios del vehículo.");
    else if (modo === "permiso") enviar(() => registrarPermiso(vehiculo.matricula, permiso), "Guardamos el permiso de circulación.");
    else if (modo === "itv") enviar(() => agregarItv(vehiculo.matricula, itv), "Registramos la ITV.");
    else if (modo === "baja") enviar(() => darDeBaja(vehiculo.matricula), "El vehículo quedó dado de baja.");
  };

  const errorCampo = (n: string) => campos[n] && <span className="text-xs font-normal text-red-700 dark:text-red-300">{campos[n]}</span>;

  return (
    <section className="flex flex-col gap-4 rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950">
      <div className="flex flex-wrap items-center gap-3">
        <h2 className="mr-auto text-lg font-semibold text-black dark:text-zinc-50">Gestionar vehículo</h2>
        <button type="button" onClick={() => abrir("editar")} className={`${botonSecundario} h-9 px-4`}>
          Editar datos
        </button>
        <button type="button" onClick={() => abrir("permiso")} className={`${botonSecundario} h-9 px-4`}>
          {vehiculo.permiso ? "Actualizar permiso" : "Cargar permiso"}
        </button>
        <button type="button" onClick={() => abrir("itv")} className={`${botonSecundario} h-9 px-4`}>
          Agregar ITV
        </button>
        <button type="button" onClick={() => abrir("baja")} className={`${botonSecundario} h-9 px-4 text-red-700 dark:text-red-300`}>
          Dar de baja
        </button>
      </div>

      {ok && (
        <p role="status" className="rounded-lg bg-green-50 p-3 text-sm text-green-900 dark:bg-green-950 dark:text-green-200">
          {ok}
        </p>
      )}
      {error && (
        <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
          {error}
        </p>
      )}

      {modo === "editar" && (
        <div className="grid gap-4 sm:grid-cols-2">
          {(
            [
              ["marca", "Marca", "text"],
              ["modelo", "Modelo", "text"],
              ["peso", "Peso del vehículo (kg)", "number"],
              ["capacidadCargaKg", "Capacidad de carga (kg)", "number"],
            ] as const
          ).map(([n, texto, tipo]) => (
            <label key={n} className={etiqueta}>
              {texto}
              <input
                type={tipo}
                value={datos[n]}
                min={tipo === "number" ? 1 : undefined}
                step={tipo === "number" ? 1 : undefined}
                onChange={(e) => setDatos({ ...datos, [n]: e.target.value })}
                className={campo}
              />
              {errorCampo(n)}
            </label>
          ))}
        </div>
      )}

      {modo === "permiso" && (
        <div className="grid gap-4 sm:grid-cols-3">
          <label className={etiqueta}>
            Número de permiso
            <input value={permiso.nro} onChange={(e) => setPermiso({ ...permiso, nro: e.target.value })} className={`${campo} uppercase`} />
            {errorCampo("nro")}
          </label>
          <label className={etiqueta}>
            Válido desde
            <input
              type="date"
              value={permiso.fechaEmision}
              onChange={(e) => setPermiso({ ...permiso, fechaEmision: e.target.value })}
              className={campo}
            />
            {errorCampo("fechaEmision")}
          </label>
          <label className={etiqueta}>
            Válido hasta
            <input
              type="date"
              value={permiso.fechaVencimiento}
              min={permiso.fechaEmision || undefined}
              onChange={(e) => setPermiso({ ...permiso, fechaVencimiento: e.target.value })}
              className={campo}
            />
            {errorCampo("fechaVencimiento")}
          </label>
          {vehiculo.permiso && (
            <p className="text-xs text-zinc-500 sm:col-span-3">
              El vehículo tiene un solo permiso: lo que guardes reemplaza al actual ({vehiculo.permiso.nro}).
            </p>
          )}
        </div>
      )}

      {modo === "itv" && (
        <label className={`${etiqueta} sm:max-w-xs`}>
          Fecha de vencimiento de la ITV
          <input type="date" value={itv} onChange={(e) => setItv(e.target.value)} className={campo} />
          {errorCampo("fechaVencimiento")}
        </label>
      )}

      {modo === "baja" && (
        <p className="rounded-lg border border-black/10 p-4 text-sm text-zinc-700 dark:border-white/15 dark:text-zinc-300">
          ¿Dar de baja el vehículo {vehiculo.matricula}? No se podrá asignar a nuevos viajes ni modificar, pero se conserva
          su historial. No se puede dar de baja si tiene viajes asignados o en curso.
        </p>
      )}

      {modo && (
        <div className="flex gap-3">
          <button type="button" disabled={pendiente} onClick={guardar} className={botonGubUy}>
            {pendiente ? "Guardando…" : modo === "baja" ? "Confirmar baja" : "Guardar"}
          </button>
          <button type="button" disabled={pendiente} onClick={() => abrir(null)} className={botonSecundario}>
            Volver
          </button>
        </div>
      )}
    </section>
  );
}

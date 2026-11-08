"use client";

import { useState, useTransition } from "react";
import { botonPrimario, botonSecundario } from "@/components/encabezado";
import { MAX_DIRECCION, MAX_NOMBRE, nombreUsuario, type UsuarioDisponible } from "@/lib/empresas";
import { crearEmpresa, type ResultadoRubros, type ResultadoUsuarios } from "../actions";
import { ElegirResponsable } from "../elegir-responsable";
import { ElegirRubros } from "../elegir-rubros";

const entrada =
  "h-10 rounded-lg border border-black/15 bg-transparent px-3 text-base font-normal text-black disabled:opacity-60 dark:border-white/20 dark:text-zinc-50";
const etiqueta = "flex flex-col gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300";

// FA1: el funcionario ingresa los datos de la empresa, elige (opcionalmente) su responsable entre los
// usuarios disponibles y confirma. Las reglas las valida el backend; si sale bien, la action redirige
// al detalle de la empresa nueva.
export function CrearEmpresa({ disponibles, rubros }: { disponibles: ResultadoUsuarios; rubros: ResultadoRubros }) {
  const [datos, setDatos] = useState({
    nroEmpresa: "",
    nombrePublico: "",
    razonSocial: "",
    direccion: "",
    habilitada: false,
  });
  const [responsable, setResponsable] = useState<UsuarioDisponible | null>(null);
  const [rubrosElegidos, setRubrosElegidos] = useState<number[]>([]);
  const [rechazados, setRechazados] = useState<string[]>([]);
  const [confirmar, setConfirmar] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [campos, setCampos] = useState<Record<string, string>>({});
  const [pendiente, iniciar] = useTransition();

  const cambiar = <K extends keyof typeof datos>(campo: K, valor: (typeof datos)[K]) =>
    setDatos((d) => ({ ...d, [campo]: valor }));

  const revisar = (e: React.FormEvent) => {
    e.preventDefault();
    const nro = Number(datos.nroEmpresa);
    if (!/^\d{1,15}$/.test(datos.nroEmpresa) || nro < 1) {
      setCampos({ nroEmpresa: "El número de empresa tiene que ser un entero positivo." });
      setError("Revisá los datos marcados.");
      return;
    }
    setError(null);
    setCampos({});
    setConfirmar(true);
  };

  const enviar = () => {
    setError(null);
    iniciar(async () => {
      // Si sale bien, la action redirige y esto no vuelve
      const r = await crearEmpresa({
        ...datos,
        nroEmpresa: Number(datos.nroEmpresa),
        habilitada: datos.habilitada && responsable !== null,
        ciResponsable: responsable?.ci ?? null,
        rubros: rubrosElegidos,
      });
      setError(r.error);
      setCampos(r.campos ?? {});
      setConfirmar(false);
      // El responsable elegido dejo de estar disponible: se quita (y con el la habilitacion, que lo necesita)
      // y no se lo vuelve a ofrecer
      if (r.campos?.ciResponsable && responsable) {
        const ci = responsable.ci;
        setRechazados((rs) => [...rs, ci]);
        setResponsable(null);
        cambiar("habilitada", false);
      }
    });
  };

  const errorCampo = (n: string) =>
    campos[n] && <span className="text-xs font-normal text-red-700 dark:text-red-300">{campos[n]}</span>;
  const bloqueado = pendiente || confirmar;

  return (
    <form onSubmit={revisar} className="flex flex-col gap-6">
      {error && (
        <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
          {error}
        </p>
      )}

      <section className="flex flex-col gap-4 rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950">
        <h2 className="text-lg font-semibold text-black dark:text-zinc-50">Datos de la empresa</h2>

        <label className={etiqueta}>
          Número de empresa
          <input
            value={datos.nroEmpresa}
            inputMode="numeric"
            maxLength={15}
            required
            disabled={bloqueado}
            onChange={(e) => cambiar("nroEmpresa", e.target.value.replace(/\D/g, ""))}
            className={`${entrada} max-w-48`}
          />
          <span className="text-xs font-normal text-zinc-500">Lo asigna el MTOP y no se puede modificar después.</span>
          {errorCampo("nroEmpresa")}
        </label>

        <label className={etiqueta}>
          Nombre público
          <input
            value={datos.nombrePublico}
            maxLength={MAX_NOMBRE}
            required
            disabled={bloqueado}
            onChange={(e) => cambiar("nombrePublico", e.target.value)}
            className={entrada}
          />
          {errorCampo("nombrePublico")}
        </label>

        <label className={etiqueta}>
          Razón social
          <input
            value={datos.razonSocial}
            maxLength={MAX_NOMBRE}
            required
            disabled={bloqueado}
            onChange={(e) => cambiar("razonSocial", e.target.value)}
            className={entrada}
          />
          {errorCampo("razonSocial")}
        </label>

        <label className={etiqueta}>
          Dirección
          <input
            value={datos.direccion}
            maxLength={MAX_DIRECCION}
            required
            disabled={bloqueado}
            onChange={(e) => cambiar("direccion", e.target.value)}
            className={entrada}
          />
          {errorCampo("direccion")}
        </label>

        <ElegirRubros
          catalogo={rubros}
          elegidos={rubrosElegidos}
          onCambiar={setRubrosElegidos}
          deshabilitado={bloqueado}
          error={campos.rubros}
        />

        <label className="flex items-start gap-2 text-sm text-zinc-700 dark:text-zinc-300">
          <input
            type="checkbox"
            checked={datos.habilitada && responsable !== null}
            disabled={bloqueado || responsable === null}
            onChange={(e) => cambiar("habilitada", e.target.checked)}
            className="mt-0.5"
          />
          <span>
            <span className="font-medium">Empresa habilitada</span>
            <span className="block text-zinc-500">
              {responsable
                ? "Solo una empresa habilitada puede dar de alta choferes."
                : "Para habilitarla, elegí primero su responsable."}
            </span>
            {errorCampo("habilitada")}
          </span>
        </label>
      </section>

      <ElegirResponsable
        inicial={disponibles}
        elegido={responsable}
        onElegir={(u) => {
          setResponsable(u);
          // Sin responsable la empresa no se puede habilitar
          if (!u) cambiar("habilitada", false);
        }}
        deshabilitado={bloqueado}
        error={campos.ciResponsable}
        excluidos={rechazados}
      />

      {!confirmar ? (
        <div className="flex gap-3">
          <button type="submit" disabled={pendiente} className={botonPrimario}>
            Registrar empresa
          </button>
        </div>
      ) : (
        <div className="flex flex-col gap-3 rounded-2xl border border-black/10 bg-white p-6 shadow-sm dark:border-white/15 dark:bg-zinc-950">
          <p className="text-sm text-zinc-700 dark:text-zinc-300">
            ¿Registrar la empresa N° {datos.nroEmpresa} &quot;{datos.nombrePublico.trim()}&quot;
            {responsable
              ? ` con ${nombreUsuario(responsable)} (C.I. ${responsable.ci}) como responsable`
              : " sin responsable"}
            ?
          </p>
          <div className="flex gap-3">
            <button type="button" disabled={pendiente} onClick={enviar} className={botonPrimario}>
              {pendiente ? "Registrando…" : "Confirmar"}
            </button>
            <button type="button" disabled={pendiente} onClick={() => setConfirmar(false)} className={botonSecundario}>
              Volver
            </button>
          </div>
        </div>
      )}
    </form>
  );
}

"use client";

import { useEffect, useRef, useState, useTransition } from "react";
import { botonPrimario, botonSecundario } from "@/components/encabezado";
import { MAX_DIRECCION, MAX_NOMBRE, nombreUsuario, type UsuarioDisponible } from "@/lib/empresas";
import { buscarUsuariosDisponibles, crearEmpresa, type ResultadoUsuarios } from "../actions";

const entrada =
  "h-10 rounded-lg border border-black/15 bg-transparent px-3 text-base font-normal text-black disabled:opacity-60 dark:border-white/20 dark:text-zinc-50";
const etiqueta = "flex flex-col gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300";

// FA1: el funcionario ingresa los datos de la empresa, elige (opcionalmente) su responsable entre los
// usuarios disponibles y confirma. Las reglas las valida el backend; si sale bien, la action redirige
// al detalle de la empresa nueva.
export function CrearEmpresa({ disponibles }: { disponibles: ResultadoUsuarios }) {
  const [datos, setDatos] = useState({
    nroEmpresa: "",
    nombrePublico: "",
    razonSocial: "",
    direccion: "",
    habilitada: false,
  });
  const [responsable, setResponsable] = useState<UsuarioDisponible | null>(null);
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
        ciResponsable: responsable?.ci ?? null,
      });
      setError(r.error);
      setCampos(r.campos ?? {});
      setConfirmar(false);
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

        <label className="flex items-start gap-2 text-sm text-zinc-700 dark:text-zinc-300">
          <input
            type="checkbox"
            checked={datos.habilitada}
            disabled={bloqueado}
            onChange={(e) => cambiar("habilitada", e.target.checked)}
            className="mt-0.5"
          />
          <span>
            <span className="font-medium">Empresa habilitada</span>
            <span className="block text-zinc-500">Solo una empresa habilitada puede dar de alta choferes.</span>
          </span>
        </label>
      </section>

      <ElegirResponsable
        inicial={disponibles}
        elegido={responsable}
        onElegir={setResponsable}
        deshabilitado={bloqueado}
        error={campos.ciResponsable}
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

// Buscador de usuarios registrados y validados que todavia no son responsables de ninguna empresa
function ElegirResponsable({
  inicial,
  elegido,
  onElegir,
  deshabilitado,
  error,
}: {
  inicial: ResultadoUsuarios;
  elegido: UsuarioDisponible | null;
  onElegir: (u: UsuarioDisponible | null) => void;
  deshabilitado: boolean;
  error?: string;
}) {
  const [filtro, setFiltro] = useState("");
  const [resultado, setResultado] = useState<ResultadoUsuarios>(inicial);
  const [buscando, setBuscando] = useState(false);
  // Descarta respuestas de busquedas viejas que lleguen despues de una mas nueva
  const ultima = useRef(0);
  const espera = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  useEffect(() => () => clearTimeout(espera.current), []);

  // Busca 300 ms despues de la ultima tecla; sin filtro vuelve a la primera tanda que trajo la pagina
  const buscar = (valor: string) => {
    setFiltro(valor);
    clearTimeout(espera.current);
    const id = ++ultima.current;
    if (valor === "") {
      setResultado(inicial);
      setBuscando(false);
      return;
    }
    espera.current = setTimeout(async () => {
      setBuscando(true);
      const r = await buscarUsuariosDisponibles(valor);
      if (id === ultima.current) {
        setResultado(r);
        setBuscando(false);
      }
    }, 300);
  };

  return (
    <section className="flex flex-col gap-4 rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950">
      <div className="flex flex-col gap-1">
        <h2 className="text-lg font-semibold text-black dark:text-zinc-50">Responsable (opcional)</h2>
        <p className="text-sm text-zinc-600 dark:text-zinc-400">
          Elegí un usuario registrado y validado con la PDI que no sea responsable de otra empresa.
        </p>
      </div>

      {elegido ? (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-[#0068b3] bg-[#0068b3]/5 p-3 text-sm">
          <span>
            <span className="font-medium text-black dark:text-zinc-50">{nombreUsuario(elegido)}</span>
            <span className="text-zinc-500"> · C.I. <span className="font-mono">{elegido.ci}</span></span>
          </span>
          <button type="button" disabled={deshabilitado} onClick={() => onElegir(null)} className={botonSecundario}>
            Quitar
          </button>
        </div>
      ) : (
        <>
          <label className={etiqueta}>
            Buscar por cédula
            <input
              value={filtro}
              inputMode="numeric"
              maxLength={8}
              disabled={deshabilitado}
              placeholder="Comienzo de la cédula"
              onChange={(e) => buscar(e.target.value.replace(/\D/g, ""))}
              className={`${entrada} max-w-64`}
            />
          </label>

          {buscando ? (
            <p className="text-sm text-zinc-500">Buscando…</p>
          ) : !resultado.ok ? (
            <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
              {resultado.error}
            </p>
          ) : resultado.usuarios.length === 0 ? (
            <p className="text-sm text-zinc-600 dark:text-zinc-400">
              {filtro ? "No hay usuarios disponibles con esa cédula." : "No hay usuarios disponibles."}
            </p>
          ) : (
            <ul className="flex max-h-72 flex-col divide-y divide-black/5 overflow-y-auto rounded-lg border border-black/10 dark:divide-white/10 dark:border-white/15">
              {resultado.usuarios.map((u) => (
                <li key={u.ci}>
                  <button
                    type="button"
                    disabled={deshabilitado}
                    onClick={() => onElegir(u)}
                    className="flex w-full items-center justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-zinc-50 disabled:opacity-60 dark:hover:bg-zinc-900"
                  >
                    <span className="text-black dark:text-zinc-50">{nombreUsuario(u)}</span>
                    <span className="font-mono text-zinc-500">C.I. {u.ci}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </>
      )}

      {error && <span className="text-xs text-red-700 dark:text-red-300">{error}</span>}
    </section>
  );
}

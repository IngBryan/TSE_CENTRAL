import { randomUUID } from "node:crypto";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { Encabezado } from "@/components/encabezado";
import { Habilitada } from "@/components/habilitada";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend, type RespuestaBackend } from "@/lib/backend";
import type { ErrorBackend } from "@/lib/casos";
import { ESTADOS_VALIDACION, type EmpresaDetalle } from "@/lib/empresas";
import { esFiltro, type FiscalizacionEmpresa } from "@/lib/vehiculos";
import { buscarUsuariosDisponibles, type ResultadoUsuarios } from "../actions";
import { AccionesEmpresa } from "./acciones-empresa";
import { VehiculosEmpresa } from "./vehiculos-empresa";
import { registrarLog } from "@/lib/log";

const NO_DISPONIBLE = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";

const tarjeta = "rounded-2xl bg-white p-6 text-sm shadow-sm dark:bg-zinc-950";
const titulo = "text-lg font-semibold text-black dark:text-zinc-50";
const dato = "text-zinc-500";

// CU Gestionar empresas de transporte: consulta de la empresa (FA2), su modificacion (FA3) y, si no tiene
// responsable, la asociacion de uno (FA4). Que quien
// consulta sea funcionario lo controla el backend (403 si no, FA5).
export default async function DetalleEmpresa({ params, searchParams }: PageProps<"/empresas/[nro]">) {
  const sesion = await getSesion();
  if (!sesion) redirect("/");
  const { nro } = await params;
  const query = await searchParams;
  // ?creada=1: viene del alta (FA1, paso 7)
  const creada = query.creada === "1";
  // ?estado=: filtro de los vehiculos (fiscalizacion de habilitaciones)
  const filtro = esFiltro(query.estado) ? query.estado : "todos";
  // Hasta 18 digitos para que entre en el Long del backend
  if (!/^\d{1,18}$/.test(nro)) notFound();

  // FA4: sin responsable, la primera tanda de usuarios que se pueden asociar viene con la pagina. Se pide en
  // paralelo con el detalle (todavia no se sabe si tiene responsable) y se descarta si lo tiene. El catch
  // evita un rechazo sin atender si la pagina termina antes de esperarla.
  const pedidoDisponibles = buscarUsuariosDisponibles("").catch(
    (): ResultadoUsuarios => ({ ok: false, error: "No se pudieron obtener los usuarios registrados." }),
  );

  const correlationId = randomUUID();
  // Los vehiculos se piden en paralelo con el detalle; si fallan, la pagina de la empresa se muestra igual
  const pedidoVehiculos = vehiculosDeEmpresa(nro, sesion.usuario.cedula, correlationId).catch(
    (e): ResultadoVehiculos => {
      if (!(e instanceof BackendNoDisponible)) registrarLog("error", "empresas", { correlationId, error: (e as Error).message });
      return { error: NO_DISPONIBLE };
    },
  );
  let res: RespuestaBackend<EmpresaDetalle & ErrorBackend>;
  try {
    res = await llamarBackend<EmpresaDetalle & ErrorBackend>(`/empresas/${nro}`, {
      correlationId,
      usuarioCi: sesion.usuario.cedula,
    });
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return <Pagina sesion={sesion} nro={nro} error={NO_DISPONIBLE} />;
  }

  if (res.status === 401) {
    // Error de configuracion, no del usuario: la X-Api-Key no coincide con la de WildFly
    registrarLog("error", "empresas", { correlationId }, { detalle: `el backend rechazo la X-Api-Key: revisar BACKEND_API_KEY` });
  }
  if (res.status !== 200 || !res.data) {
    const error =
      res.status === 404
        ? "La empresa no existe."
        : res.status === 403
          ? "No tenés acceso a la gestión de empresas: no estás registrado como funcionario del MTOP."
          : "No se pudo obtener la empresa.";
    return <Pagina sesion={sesion} nro={nro} error={error} />;
  }

  const empresa = res.data;
  const r = empresa.responsable;
  const disponibles = r ? null : await pedidoDisponibles;
  const vehiculos = await pedidoVehiculos;
  return (
    <Pagina sesion={sesion} nro={nro} empresa={empresa}>
      <section className={`grid gap-x-6 gap-y-2 sm:grid-cols-2 ${tarjeta}`}>
        <p><span className={dato}>Nombre público:</span> {empresa.nombrePublico ?? "-"}</p>
        <p><span className={dato}>Razón social:</span> {empresa.razonSocial ?? "-"}</p>
        <p className="sm:col-span-2"><span className={dato}>Dirección:</span> {empresa.direccion ?? "-"}</p>
      </section>

      <section className={`flex flex-col gap-2 ${tarjeta}`}>
        <h2 className={titulo}>Responsable</h2>
        {r ? (
          <p>
            {[r.primerNombre, r.primerApellido].filter(Boolean).join(" ") || "Sin nombre registrado"}
            <span className="text-zinc-500">
              {" "}· C.I. <span className="font-mono">{r.ci}</span>
              {r.estado && ` · ${ESTADOS_VALIDACION[r.estado] ?? r.estado}`}
            </span>
          </p>
        ) : (
          <p className="text-zinc-600 dark:text-zinc-400">La empresa no tiene un responsable asociado.</p>
        )}
      </section>

      <AccionesEmpresa empresa={empresa} disponibles={disponibles} creada={creada} />

      <VehiculosEmpresa nro={nro} fiscalizacion={vehiculos.fiscalizacion} error={vehiculos.error} filtro={filtro} />
    </Pagina>
  );
}

type ResultadoVehiculos = { fiscalizacion?: FiscalizacionEmpresa; error?: string };

// GET /empresas/{nro}/vehiculos con el mismo correlation id que el detalle
async function vehiculosDeEmpresa(nro: string, ci: string, correlationId: string): Promise<ResultadoVehiculos> {
  const res = await llamarBackend<FiscalizacionEmpresa & ErrorBackend>(`/empresas/${nro}/vehiculos`, {
    correlationId,
    usuarioCi: ci,
  });
  if (res.status !== 200 || !res.data) return { error: "No se pudieron obtener los vehículos de la empresa." };
  return { fiscalizacion: res.data };
}

function Pagina({
  sesion,
  nro,
  empresa,
  error,
  children,
}: {
  sesion: NonNullable<Awaited<ReturnType<typeof getSesion>>>;
  nro: string;
  empresa?: EmpresaDetalle;
  error?: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 px-4 py-12">
        <div className="flex flex-col gap-1">
          <Link href="/empresas" className="text-sm text-zinc-500 hover:underline">
            ← Empresas de transporte
          </Link>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-3xl font-semibold tracking-tight text-black dark:text-zinc-50">
              {empresa?.nombrePublico ?? `Empresa N° ${nro}`}
            </h1>
            {empresa && <Habilitada habilitada={empresa.habilitada} />}
          </div>
          {empresa && <p className="text-zinc-600 dark:text-zinc-400">Empresa N° {empresa.nroEmpresa}</p>}
        </div>

        {error ? (
          <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
            {error}
          </p>
        ) : (
          children
        )}
      </main>
    </div>
  );
}

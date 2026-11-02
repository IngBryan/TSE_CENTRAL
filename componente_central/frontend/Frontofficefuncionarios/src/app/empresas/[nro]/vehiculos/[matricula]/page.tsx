import { randomUUID } from "node:crypto";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { Encabezado } from "@/components/encabezado";
import { EstadoPeriodo, EstadoVehiculoIndicador } from "@/components/estado-vigencia";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend, type RespuestaBackend } from "@/lib/backend";
import type { ErrorBackend } from "@/lib/casos";
import { fecha, type PeriodoVista, type VehiculoDetalle } from "@/lib/vehiculos";

const NO_DISPONIBLE = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";

const tarjeta = "rounded-2xl bg-white p-6 text-sm shadow-sm dark:bg-zinc-950";
const titulo = "text-base font-semibold text-black dark:text-zinc-50";
const dato = "text-zinc-500";

// CU Fiscalizacion de los permisos y estado de los vehiculos: un vehiculo de la empresa con su permiso
// nacional de circulacion y el historial de sus ITV. Que quien consulta sea funcionario lo controla el
// backend (403 si no).
export default async function DetalleVehiculo({ params }: PageProps<"/empresas/[nro]/vehiculos/[matricula]">) {
  const sesion = await getSesion();
  if (!sesion) redirect("/");
  const { nro, matricula } = await params;
  // Hasta 18 digitos para que entre en el Long del backend; la matricula, letras y digitos (el backend la
  // normaliza como en el alta)
  if (!/^\d{1,18}$/.test(nro) || !/^[A-Za-z0-9-]{1,20}$/.test(matricula)) notFound();

  const correlationId = randomUUID();
  const ruta = `/empresas/${nro}/vehiculos/${encodeURIComponent(matricula)}`;
  let res: RespuestaBackend<VehiculoDetalle & ErrorBackend>;
  try {
    res = await llamarBackend<VehiculoDetalle & ErrorBackend>(ruta, {
      correlationId,
      usuarioCi: sesion.usuario.cedula,
    });
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return <Pagina sesion={sesion} nro={nro} matricula={matricula} error={NO_DISPONIBLE} />;
  }
  console.info(`[vehiculos] correlationId=${correlationId} GET ${ruta} status=${res.status}`);

  if (res.status !== 200 || !res.data) {
    const error =
      res.status === 404
        ? res.data?.error === "EMPRESA_NO_ENCONTRADA"
          ? "La empresa no existe."
          : "La empresa no tiene un vehículo con esa matrícula."
        : res.status === 403
          ? "No tenés acceso a la fiscalización de vehículos: no estás registrado como funcionario del MTOP."
          : "No se pudo obtener el vehículo.";
    return <Pagina sesion={sesion} nro={nro} matricula={matricula} error={error} />;
  }

  const { vehiculo: v, itvs } = res.data;
  return (
    <Pagina sesion={sesion} nro={nro} matricula={v.matricula} detalle={res.data}>
      <section className={`flex flex-col gap-6 ${tarjeta}`}>
        <p className="text-zinc-600 dark:text-zinc-400">
          {[v.marca, v.modelo].filter(Boolean).join(" ") || "Sin marca ni modelo registrados"}
          {" · "}
          {v.estado === "HABILITADO"
            ? "Puede circular."
            : v.estado === "DADO_DE_BAJA"
              ? "La empresa lo dio de baja."
              : "No puede circular: necesita el permiso de circulación vigente y una ITV sin vencer."}
        </p>
        <div className="grid gap-6 sm:grid-cols-2">
          <Documento titulo="Permiso nacional de circulación" periodo={v.permiso} />
          <Documento titulo="ITV" periodo={v.itv} />
        </div>
      </section>

      {itvs.length > 1 && (
        <section className={`flex flex-col gap-2 ${tarjeta}`}>
          <h2 className={titulo}>Historial de ITV</h2>
          <ul className="flex flex-col divide-y divide-black/5 dark:divide-white/10">
            {itvs.map((i, n) => (
              <li key={`${i.hasta}-${n}`} className="flex flex-wrap items-center justify-between gap-2 py-2">
                <span className="text-zinc-700 dark:text-zinc-300">{rango(i)}</span>
                {i.estado === "VENCIDO" ? <span className="text-zinc-500">Vencida</span> : <EstadoPeriodo periodo={i} />}
              </li>
            ))}
          </ul>
        </section>
      )}
    </Pagina>
  );
}

function Documento({ titulo: nombre, periodo }: { titulo: string; periodo: PeriodoVista }) {
  return (
    <div className="flex flex-col gap-2">
      <h2 className={titulo}>{nombre}</h2>
      <EstadoPeriodo periodo={periodo} />
      {periodo.estado !== "SIN_REGISTRO" && (
        <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
          {periodo.nro && (
            <>
              <dt className={dato}>Número</dt>
              <dd className="font-mono">{periodo.nro}</dd>
            </>
          )}
          <dt className={dato}>Vigencia</dt>
          <dd>{rango(periodo)}</dd>
        </dl>
      )}
    </div>
  );
}

function rango(p: PeriodoVista): string {
  return p.desde ? `${fecha(p.desde)} – ${fecha(p.hasta)}` : `Hasta el ${fecha(p.hasta)}`;
}

function Pagina({
  sesion,
  nro,
  matricula,
  detalle,
  error,
  children,
}: {
  sesion: NonNullable<Awaited<ReturnType<typeof getSesion>>>;
  nro: string;
  matricula: string;
  detalle?: VehiculoDetalle;
  error?: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 px-4 py-12">
        <div className="flex flex-col gap-1">
          <Link href={`/empresas/${nro}#vehiculos`} className="text-sm text-zinc-500 hover:underline">
            ← Vehículos de la empresa N° {nro}
          </Link>
          <div className="flex flex-wrap items-center gap-4">
            <h1 className="font-mono text-3xl font-semibold tracking-tight text-black dark:text-zinc-50">
              {matricula}
            </h1>
            {detalle && <EstadoVehiculoIndicador estado={detalle.vehiculo.estado} />}
          </div>
          {detalle && <p className="text-xs text-zinc-500">Vigencia al {fecha(detalle.fecha)}</p>}
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

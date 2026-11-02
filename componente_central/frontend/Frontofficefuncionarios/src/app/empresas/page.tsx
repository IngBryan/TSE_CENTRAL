import { randomUUID } from "node:crypto";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Encabezado, botonPrimario } from "@/components/encabezado";
import { Habilitada } from "@/components/habilitada";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend, type RespuestaBackend } from "@/lib/backend";
import type { ErrorBackend } from "@/lib/casos";
import type { EmpresaResumen } from "@/lib/empresas";
import type { ResumenHabilitaciones } from "@/lib/vehiculos";

const NO_DISPONIBLE = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";

// CU Gestionar empresas de transporte: listado de las empresas registradas (flujo principal y FA2).
// Que quien consulta sea funcionario lo controla el backend (403 si no, FA5).
export default async function Empresas() {
  const sesion = await getSesion();
  if (!sesion) redirect("/");

  const correlationId = randomUUID();
  let res: RespuestaBackend<EmpresaResumen[] & ErrorBackend>;
  try {
    res = await llamarBackend<EmpresaResumen[] & ErrorBackend>("/empresas", {
      correlationId,
      usuarioCi: sesion.usuario.cedula,
    });
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return <Pagina sesion={sesion} error={NO_DISPONIBLE} />;
  }
  console.info(`[empresas] correlationId=${correlationId} GET /empresas status=${res.status}`);

  if (res.status === 401) {
    // Error de configuracion, no del usuario: la X-Api-Key no coincide con la de WildFly
    console.error(`[empresas] correlationId=${correlationId} el backend rechazo la X-Api-Key: revisar BACKEND_API_KEY`);
  }
  if (res.status !== 200 || !Array.isArray(res.data)) {
    const error =
      res.status === 403
        ? "No tenés acceso a la gestión de empresas: no estás registrado como funcionario del MTOP."
        : "No se pudieron obtener las empresas.";
    return <Pagina sesion={sesion} error={error} />;
  }

  const empresas = res.data;
  return (
    <Pagina sesion={sesion}>
      {empresas.length === 0 ? (
        <p className="rounded-lg bg-zinc-100 p-4 text-sm text-zinc-700 dark:bg-zinc-900 dark:text-zinc-300">
          No hay empresas de transporte registradas.
        </p>
      ) : (
        <>
          <div className="overflow-x-auto rounded-2xl bg-white shadow-sm dark:bg-zinc-950">
            <table className="w-full text-left text-sm">
              <thead className="text-zinc-500">
                <tr>
                  <th className="px-4 py-3 font-normal">N°</th>
                  <th className="px-4 py-3 font-normal">Nombre público</th>
                  <th className="px-4 py-3 font-normal">Razón social</th>
                  <th className="px-4 py-3 font-normal">Responsable</th>
                  <th className="px-4 py-3 font-normal">Vehículos</th>
                  <th className="px-4 py-3 font-normal">Estado</th>
                </tr>
              </thead>
              <tbody>
                {empresas.map((e) => (
                  <tr
                    key={e.nroEmpresa}
                    className="border-t border-black/5 hover:bg-zinc-50 dark:border-white/10 dark:hover:bg-zinc-900"
                  >
                    <td className="px-4 py-3">
                      <Link
                        href={`/empresas/${e.nroEmpresa}`}
                        className="font-medium text-[#0068b3] hover:underline dark:text-sky-400"
                      >
                        {e.nroEmpresa}
                      </Link>
                    </td>
                    <td className="px-4 py-3">
                      <Link href={`/empresas/${e.nroEmpresa}`} className="text-black hover:underline dark:text-zinc-50">
                        {e.nombrePublico ?? "-"}
                      </Link>
                    </td>
                    <td className="px-4 py-3">{e.razonSocial ?? "-"}</td>
                    <td className="px-4 py-3">
                      {e.ciResponsable ? (
                        <span className="font-mono">C.I. {e.ciResponsable}</span>
                      ) : (
                        <span className="text-zinc-500">Sin responsable</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <ResumenVehiculos nroEmpresa={e.nroEmpresa} resumen={e.vehiculos} />
                    </td>
                    <td className="px-4 py-3">
                      <Habilitada habilitada={e.habilitada} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="text-sm text-zinc-600 dark:text-zinc-400">
            {empresas.length === 1 ? "1 empresa" : `${empresas.length} empresas`}
          </p>
        </>
      )}
    </Pagina>
  );
}

// Fiscalizacion de habilitaciones: cuantos vehiculos activos estan habilitados y, debajo, una nota con los
// que no lo estan o estan por vencer. Cada parte lleva al listado de vehiculos de la empresa ya filtrado.
function ResumenVehiculos({ nroEmpresa, resumen }: { nroEmpresa: number; resumen: ResumenHabilitaciones }) {
  if (resumen.total === 0) return <span className="text-zinc-500">Sin vehículos</span>;
  const activos = resumen.habilitados + resumen.noHabilitados;
  const avisos = [
    resumen.noHabilitados > 0 && {
      filtro: "no_habilitados",
      texto: resumen.noHabilitados === 1 ? "1 no habilitado" : `${resumen.noHabilitados} no habilitados`,
    },
    resumen.porVencer > 0 && { filtro: "por_vencer", texto: `${resumen.porVencer} por vencer` },
  ].filter((a) => a !== false);
  return (
    <div className="flex flex-col whitespace-nowrap">
      <Link href={`/empresas/${nroEmpresa}#vehiculos`} className="hover:underline">
        {resumen.habilitados} de {activos} habilitados
      </Link>
      {avisos.length > 0 && (
        <span className="text-xs text-zinc-500">
          {avisos.map((a, i) => (
            <span key={a.filtro}>
              {i > 0 && " · "}
              <Link href={`/empresas/${nroEmpresa}?estado=${a.filtro}#vehiculos`} className="hover:underline">
                {a.texto}
              </Link>
            </span>
          ))}
        </span>
      )}
    </div>
  );
}

function Pagina({
  sesion,
  error,
  children,
}: {
  sesion: NonNullable<Awaited<ReturnType<typeof getSesion>>>;
  error?: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-6 px-4 py-12">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="text-3xl font-semibold tracking-tight text-black dark:text-zinc-50">Empresas de transporte</h1>
          <Link href="/empresas/nueva" className={botonPrimario}>
            Nueva empresa
          </Link>
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

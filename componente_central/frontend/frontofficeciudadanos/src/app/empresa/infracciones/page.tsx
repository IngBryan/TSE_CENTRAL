import { randomUUID } from "node:crypto";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Encabezado } from "@/components/encabezado";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import { ESTADOS, fechaHora, tipoInfraccion, type InfraccionResumen } from "@/lib/infracciones";

// Gestion de Infracciones: casos de fiscalizacion notificados a la empresa del responsable (el backend
// decide si puede verlos).
export default async function Infracciones() {
  const sesion = await getSesion();
  if (!sesion) redirect("/");

  let infracciones: InfraccionResumen[] | null = null;
  let error: string | null = null;
  try {
    const res = await llamarBackend<InfraccionResumen[] & { detalle?: string }>("/empresa/infracciones", {
      correlationId: randomUUID(),
      usuarioCi: sesion.perfil.cedula,
    });
    if (res.status === 200) infracciones = res.data;
    else if (res.status === 403) error = `No tenés acceso a las infracciones: ${res.data?.detalle ?? "no sos responsable de una empresa"}.`;
    else error = "No se pudo obtener el listado de infracciones.";
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    error = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";
  }
  const empresa = sesion.perfil.roles?.find((r) => r.rol === "RESPONSABLE")?.empresa;
  const pendientes = infracciones?.filter((i) => i.admiteDescargo).length ?? 0;

  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-6 px-4 py-12">
        <div className="flex flex-col gap-1">
          <Link href="/mi-cuenta" className="text-sm text-zinc-500 hover:underline">
            ← Mi cuenta
          </Link>
          <h1 className="text-3xl font-semibold tracking-tight text-black dark:text-zinc-50">
            Infracciones{empresa ? ` de ${empresa}` : ""}
          </h1>
          <p className="text-zinc-600 dark:text-zinc-400">
            Casos de fiscalización detectados sobre los vehículos y viajes de la empresa. Podés ver la evidencia y
            presentar un descargo dentro del plazo.
          </p>
        </div>

        {error ? (
          <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
            {error}
          </p>
        ) : infracciones && infracciones.length > 0 ? (
          <>
            {pendientes > 0 && (
              <p role="status" className="rounded-lg bg-sky-50 p-4 text-sky-900 dark:bg-sky-950 dark:text-sky-200">
                {pendientes === 1
                  ? "Tenés 1 infracción a la que todavía le podés presentar un descargo."
                  : `Tenés ${pendientes} infracciones a las que todavía les podés presentar un descargo.`}
              </p>
            )}
            <div className="overflow-x-auto rounded-2xl bg-white shadow-sm dark:bg-zinc-950">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-black/5 text-zinc-500 dark:border-white/10">
                  <tr>
                    <th className="px-4 py-3 font-medium">N°</th>
                    <th className="px-4 py-3 font-medium">Infracción</th>
                    <th className="px-4 py-3 font-medium">Vehículo</th>
                    <th className="px-4 py-3 font-medium">Detectada</th>
                    <th className="px-4 py-3 font-medium">Estado</th>
                  </tr>
                </thead>
                <tbody>
                  {infracciones.map((i) => (
                    <tr key={i.id} className="border-b border-black/5 align-top last:border-0 dark:border-white/10">
                      <td className="px-4 py-3">
                        <Link href={`/empresa/infracciones/${i.id}`} className="font-medium underline">
                          {i.id}
                        </Link>
                      </td>
                      <td className="px-4 py-3">
                        <Link href={`/empresa/infracciones/${i.id}`} className="hover:underline">
                          {tipoInfraccion(i.tipoCaso)}
                        </Link>
                        {i.nueva && (
                          <span className="ml-2 rounded-full bg-[#0068b3] px-2 py-0.5 text-xs font-medium text-white">Nueva</span>
                        )}
                        {i.descripcion && <p className="line-clamp-1 text-xs text-zinc-500">{i.descripcion}</p>}
                      </td>
                      <td className="px-4 py-3 font-mono">{i.matricula ?? "-"}</td>
                      <td className="whitespace-nowrap px-4 py-3">{fechaHora(i.fechaDeteccion)}</td>
                      <td className="px-4 py-3">
                        <div className="flex flex-col items-start gap-1">
                          <span className={`whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium ${ESTADOS[i.estado].color}`}>
                            {ESTADOS[i.estado].texto}
                          </span>
                          {i.admiteDescargo && (
                            <span className="whitespace-nowrap text-xs text-zinc-500">descargo hasta {fechaHora(i.venceDescargo)}</span>
                          )}
                          {i.estado === "NOTIFICADA" && !i.admiteDescargo && (
                            <span className="whitespace-nowrap text-xs text-zinc-500">plazo de descargo vencido</span>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        ) : (
          <p className="text-zinc-600 dark:text-zinc-400">La empresa no tiene infracciones notificadas.</p>
        )}
      </main>
    </div>
  );
}

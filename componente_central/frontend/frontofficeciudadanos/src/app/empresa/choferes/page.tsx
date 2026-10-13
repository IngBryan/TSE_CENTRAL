import { randomUUID } from "node:crypto";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Encabezado } from "@/components/encabezado";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import { AltaChofer } from "./alta-chofer";

type ChoferVista = { cedula: string; primerNombre?: string; primerApellido?: string; estado: string };

const ESTADOS: Record<string, string> = {
  HABILITADO: "Habilitado",
  PENDIENTE_VALIDACION: "Pendiente de validación",
};

// CU-02: seccion "Choferes" del responsable de la empresa. El backend decide si puede verla.
export default async function Choferes() {
  const sesion = await getSesion();
  if (!sesion) redirect("/");

  let choferes: ChoferVista[] | null = null;
  let error: string | null = null;
  try {
    const res = await llamarBackend<ChoferVista[] & { detalle?: string }>("/empresa/choferes", {
      correlationId: randomUUID(),
      usuarioCi: sesion.perfil.cedula,
    });
    if (res.status === 200) choferes = res.data;
    else if (res.status === 403) error = `No tenés acceso a los choferes: ${res.data?.detalle ?? "no sos responsable de una empresa"}.`;
    else error = "No se pudo obtener el listado de choferes.";
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    error = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";
  }
  const empresa = sesion.perfil.roles?.find((r) => r.rol === "RESPONSABLE")?.empresa;

  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 px-4 py-12">
        <div className="flex flex-col gap-1">
          <Link href="/mi-cuenta" className="text-sm text-zinc-500 hover:underline">
            ← Mi cuenta
          </Link>
          <h1 className="text-3xl font-semibold tracking-tight text-black dark:text-zinc-50">
            Choferes{empresa ? ` de ${empresa}` : ""}
          </h1>
        </div>

        {error ? (
          <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
            {error}
          </p>
        ) : (
          <>
            <AltaChofer />

            {choferes && choferes.length > 0 ? (
              <div className="overflow-x-auto rounded-2xl bg-white shadow-sm dark:bg-zinc-950">
                <table className="w-full text-left text-sm">
                  <thead className="border-b border-black/5 text-zinc-500 dark:border-white/10">
                    <tr>
                      <th className="px-4 py-3 font-medium">Cédula</th>
                      <th className="px-4 py-3 font-medium">Nombre</th>
                      <th className="px-4 py-3 font-medium">Estado</th>
                    </tr>
                  </thead>
                  <tbody>
                    {choferes.map((c) => (
                      <tr key={c.cedula} className="border-b border-black/5 last:border-0 dark:border-white/10">
                        <td className="px-4 py-3 tabular-nums">{c.cedula}</td>
                        <td className="px-4 py-3">
                          {[c.primerNombre, c.primerApellido].filter(Boolean).join(" ") || "—"}
                        </td>
                        <td className="px-4 py-3">{ESTADOS[c.estado] ?? c.estado}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="text-zinc-600 dark:text-zinc-400">Todavía no hay choferes en la empresa.</p>
            )}
          </>
        )}
      </main>
    </div>
  );
}

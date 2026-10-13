import { randomUUID } from "node:crypto";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Encabezado } from "@/components/encabezado";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import type { DatosEmpresa } from "./actions";
import { FormularioEmpresa } from "./formulario-empresa";

// Perfil de la Empresa: el responsable consulta y actualiza los datos. El backend decide si puede.
export default async function PerfilEmpresa() {
  const sesion = await getSesion();
  if (!sesion) redirect("/");

  let empresa: DatosEmpresa | null = null;
  let error: string | null = null;
  try {
    const res = await llamarBackend<DatosEmpresa & { detalle?: string }>("/empresa", {
      correlationId: randomUUID(),
      usuarioCi: sesion.perfil.cedula,
    });
    if (res.status === 200) empresa = res.data;
    else if (res.status === 403) error = `No tenés acceso a los datos de la empresa: ${res.data?.detalle ?? "no sos su responsable"}.`;
    else error = "No se pudieron obtener los datos de la empresa.";
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    error = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";
  }

  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-6 px-4 py-12">
        <div className="flex flex-col gap-1">
          <Link href="/mi-cuenta" className="text-sm text-zinc-500 hover:underline">
            ← Mi cuenta
          </Link>
          <h1 className="text-3xl font-semibold tracking-tight text-black dark:text-zinc-50">Datos de la empresa</h1>
          {empresa && !empresa.habilitada && (
            <p className="text-sm text-amber-800 dark:text-amber-300">
              La empresa todavía no está habilitada por el MTOP.
            </p>
          )}
        </div>

        {error || !empresa ? (
          <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
            {error}
          </p>
        ) : (
          <FormularioEmpresa empresa={empresa} />
        )}
      </main>
    </div>
  );
}

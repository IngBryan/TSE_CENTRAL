import { randomUUID } from "node:crypto";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Encabezado } from "@/components/encabezado";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import type { LocalidadVista } from "@/lib/localidades";
import { obtenerCatalogos } from "../actions";
import { NuevaGuia } from "../nueva-guia";

export default async function CrearGuia() {
  const sesion = await getSesion();
  if (!sesion) redirect("/");
  let localidades: LocalidadVista[] = [];
  let error: string | null = null;
  try {
    const opciones = { correlationId: randomUUID(), usuarioCi: sesion.perfil.cedula };
    const acceso = await llamarBackend("/empresa/guias", opciones);
    if (acceso.status !== 200) error = "No tenés acceso a la creación de guías.";
    else {
      const res = await llamarBackend<LocalidadVista[]>("/localidades", opciones);
      if (res.status === 200 && Array.isArray(res.data)) localidades = res.data;
    }
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    error = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";
  }
  const { tiposCarga, rubros } = error ? { tiposCarga: [], rubros: [] } : await obtenerCatalogos();
  const hoy = new Date().toLocaleDateString("sv-SE", { timeZone: "America/Montevideo" });
  return <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
    <Encabezado sesion={sesion} />
    <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-6 px-4 py-12">
      <Link href="/empresa/guias" className="text-sm text-zinc-500 hover:underline">← Guías de viaje</Link>
      <h1 className="text-3xl font-semibold">Nueva guía de viaje</h1>
      {error ? <p role="alert" className="text-red-700 dark:text-red-300">{error}</p>
        : <NuevaGuia hoy={hoy} localidades={localidades} tiposCarga={tiposCarga} rubros={rubros} />}
    </main>
  </div>;
}

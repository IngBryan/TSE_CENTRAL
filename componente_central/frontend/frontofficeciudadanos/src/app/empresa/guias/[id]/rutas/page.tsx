import { randomUUID } from "node:crypto";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Encabezado } from "@/components/encabezado";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import type { Guia } from "../../actions";
import type { LocalidadVista, RutaAsignada } from "./tipos-rutas";
import { SelectorRutas } from "./selector-rutas";

type RespuestaError = { detalle?: string };

/** Consulta y autoriza rutas usando los mismos IDs de localidad que el alta y la edición de guías. */
export default async function RutasGuia({ params }: PageProps<"/empresa/guias/[id]/rutas">) {
  const sesion = await getSesion();
  if (!sesion) redirect("/");
  const { id } = await params;
  let guia: Guia | undefined;
  let localidades: LocalidadVista[] = [];
  let rutas: RutaAsignada[] = [];
  let error: string | null = null;
  try {
    const opciones = { correlationId: randomUUID(), usuarioCi: sesion.perfil.cedula };
    const [guiasRes, localidadesRes, rutasRes] = await Promise.all([
      llamarBackend<Guia[] & RespuestaError>("/empresa/guias", opciones),
      llamarBackend<LocalidadVista[] & RespuestaError>("/localidades", opciones),
      llamarBackend<RutaAsignada[] & RespuestaError>(`/empresa/guias/${encodeURIComponent(id)}/rutas`, opciones),
    ]);
    if (guiasRes.status === 200 && Array.isArray(guiasRes.data)) {
      guia = guiasRes.data.find(g => g.id === id);
    } else error = "No pudimos consultar las guías de tu empresa. Intentá nuevamente.";
    if (!guia && !error) error = "La guía no existe o no pertenece a tu empresa.";
    if (localidadesRes.status === 200 && Array.isArray(localidadesRes.data)) localidades = localidadesRes.data;
    else error ??= "No se pudo obtener el catálogo de localidades.";
    if (rutasRes.status === 200 && Array.isArray(rutasRes.data)) rutas = rutasRes.data;
    else error ??= "No pudimos consultar las rutas autorizadas. Intentá nuevamente.";
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    error = "El servicio de rutas no está disponible en este momento. Volvé a intentar más tarde.";
  }
  return <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
    <Encabezado sesion={sesion} />
    <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-6 px-4 py-12">
      <div className="space-y-2">
        <Link className="text-sm text-zinc-500 underline" href={`/empresa/guias/${encodeURIComponent(id)}`}>← Volver al detalle de la guía</Link>
        <h1 className="text-2xl font-semibold">Localidades y rutas autorizadas</h1>
        <p className="text-sm text-zinc-600 dark:text-zinc-400">
          Guía <span className="font-mono">{id}</span>. Previsualizá alternativas viales antes de confirmarlas.
          Esta pantalla no modifica el origen o el destino que figuran en la guía.
        </p>
      </div>
      {error || !guia ? <p role="alert" className="rounded-xl bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">{error}</p>
        : <SelectorRutas guia={guia} localidades={localidades} iniciales={rutas} />}
    </main>
  </div>;
}

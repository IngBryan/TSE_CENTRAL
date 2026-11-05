import { mostrarLocalidad, type LocalidadVista } from "@/lib/localidades";
import { randomUUID } from "node:crypto";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Encabezado } from "@/components/encabezado";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import type { ChoferResumen, Guia, VehiculoResumen } from "../../actions";
import { AsignarViaje } from "./asignar-viaje";

type Opciones = { guia: Guia; vehiculos: VehiculoResumen[]; choferes: ChoferResumen[] };

// CU-07 Asignar vehiculo y chofer a guia de viaje.
export default async function Asignar({ params }: PageProps<"/empresa/guias/[id]/asignar">) {
  const sesion = await getSesion();
  if (!sesion) redirect("/");
  const { id } = await params;

  let opciones: Opciones | null = null;
  let localidades: LocalidadVista[] = [];
  let error: string | null = null;
  try {
    const datosPeticion = {
      correlationId: randomUUID(),
      usuarioCi: sesion.perfil.cedula,
    };
    const [res, localidadesRes] = await Promise.all([
      llamarBackend<Opciones & { detalle?: string }>(`/empresa/guias/${encodeURIComponent(id)}/asignacion`, datosPeticion),
      llamarBackend<LocalidadVista[]>("/localidades", datosPeticion),
    ]);
    if (res.status === 200) opciones = res.data;
    else if (res.status === 409) error = res.data?.detalle ?? "La guía ya está asignada.";
    else if (res.status === 404) error = "La guía no existe.";
    else if (res.status === 403) error = `No podés asignar guías: ${res.data?.detalle ?? "no sos responsable de una empresa"}.`;
    else error = "No se pudieron obtener los datos de la guía.";
    if (localidadesRes.status === 200 && Array.isArray(localidadesRes.data)) localidades = localidadesRes.data;
    else error ??= "No se pudo obtener el catálogo de localidades para elegir las rutas.";
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    error = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";
  }

  // Precondiciones del CU: la empresa tiene al menos un vehiculo y un chofer habilitado
  if (opciones && opciones.vehiculos.length === 0) error = "La empresa no tiene vehículos activos. Registralos en la sección Vehículos.";
  else if (opciones && opciones.choferes.length === 0)
    error = "La empresa no tiene choferes habilitados. Agregá uno en Choferes (si está pendiente de validación, todavía no se le pueden asignar viajes).";

  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-6 px-4 py-12">
        <div className="flex flex-col gap-1">
          <Link href="/empresa/guias" className="text-sm text-zinc-500 hover:underline">
            ← Guías de viaje
          </Link>
          <h1 className="text-3xl font-semibold tracking-tight text-black dark:text-zinc-50">Asignar viaje</h1>
          {opciones && (
            <p className="text-zinc-600 dark:text-zinc-400">
              Guía <span className="font-mono text-sm">{opciones.guia.id}</span>: {mostrarLocalidad(opciones.guia.origen, opciones.guia.origenDepartamento)} → {mostrarLocalidad(opciones.guia.destino, opciones.guia.destinoDepartamento)},{" "}
              {opciones.guia.pesoCargaKg.toLocaleString("es-UY")} kg ({[opciones.guia.rubroCliente, opciones.guia.tipoCarga].filter(Boolean).join(", ")})
            </p>
          )}
        </div>

        {error || !opciones ? (
          <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
            {error}
          </p>
        ) : (
          <AsignarViaje guia={opciones.guia} vehiculos={opciones.vehiculos} choferes={opciones.choferes} localidades={localidades} />
        )}
      </main>
    </div>
  );
}

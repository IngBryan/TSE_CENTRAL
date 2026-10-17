import { randomUUID } from "node:crypto";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Encabezado } from "@/components/encabezado";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import type { VehiculoDetalle } from "../actions";
import { EstadoItv, EstadoPermiso, formatoFecha, Habilitacion } from "../estado";
import { GestionarVehiculo } from "./gestionar-vehiculo";

// Detalle de un vehiculo de la empresa: datos, permiso de circulacion, historial de ITV y, si sigue
// activo, las acciones para modificarlo o darlo de baja.
export default async function DetalleVehiculo({ params }: PageProps<"/empresa/vehiculos/[matricula]">) {
  const sesion = await getSesion();
  if (!sesion) redirect("/");
  const { matricula: id } = await params;

  let datos: VehiculoDetalle | null = null;
  let error: string | null = null;
  try {
    const res = await llamarBackend<VehiculoDetalle & { detalle?: string }>(`/empresa/vehiculos/${encodeURIComponent(id)}`, {
      correlationId: randomUUID(),
      usuarioCi: sesion.perfil.cedula,
    });
    if (res.status === 200) datos = res.data;
    else if (res.status === 404) error = "El vehículo no existe.";
    else if (res.status === 403) error = `No tenés acceso a los vehículos: ${res.data?.detalle ?? "no sos responsable de una empresa"}.`;
    else error = "No se pudo obtener el vehículo.";
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    error = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";
  }
  const v = datos?.vehiculo;

  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 px-4 py-12">
        <div className="flex flex-col gap-1">
          <Link href="/empresa/vehiculos" className="text-sm text-zinc-500 hover:underline">
            ← Vehículos
          </Link>
          <h1 className="text-3xl font-semibold tracking-tight text-black dark:text-zinc-50">
            Vehículo <span className="font-mono">{v?.matricula ?? id}</span>
          </h1>
        </div>

        {error || !datos || !v ? (
          <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
            {error}
          </p>
        ) : (
          <>
            <section className="grid gap-x-6 gap-y-2 rounded-2xl bg-white p-6 text-sm shadow-sm sm:grid-cols-2 dark:bg-zinc-950">
              <p className="flex items-center gap-2 sm:col-span-2">
                <span className="text-zinc-500">Estado:</span> <Habilitacion vehiculo={v} />
              </p>
              <p><span className="text-zinc-500">Marca y modelo:</span> {v.marca} {v.modelo}</p>
              <p><span className="text-zinc-500">Peso del vehículo:</span> {v.peso.toLocaleString("es-UY")} kg</p>
              <p><span className="text-zinc-500">Capacidad de carga:</span> {v.capacidadCargaKg.toLocaleString("es-UY")} kg</p>
              <p>
                <span className="text-zinc-500">Peso máximo con carga:</span>{" "}
                {(v.peso + v.capacidadCargaKg).toLocaleString("es-UY")} kg
              </p>
              <div className="flex gap-1">
                <span className="text-zinc-500">Permiso:</span> <EstadoPermiso vehiculo={v} />
              </div>
              <p><span className="text-zinc-500">ITV:</span> <EstadoItv vehiculo={v} /></p>
            </section>

            {v.activo ? (
              <GestionarVehiculo vehiculo={v} />
            ) : (
              <p role="status" className="rounded-lg bg-zinc-100 p-4 text-zinc-700 dark:bg-zinc-900 dark:text-zinc-300">
                El vehículo está dado de baja: no se puede asignar a viajes ni modificar. Se conserva su historial.
              </p>
            )}

            <section className="flex flex-col gap-3">
              <h2 className="text-lg font-semibold text-black dark:text-zinc-50">Historial de ITV</h2>
              {datos.itvs.length === 0 ? (
                <p className="text-zinc-600 dark:text-zinc-400">Todavía no se registró ninguna inspección.</p>
              ) : (
                <ul className="flex flex-col gap-2">
                  {datos.itvs.map((i) => (
                    <li key={i.id} className="flex items-center justify-between rounded-xl bg-white p-4 text-sm shadow-sm dark:bg-zinc-950">
                      <span>Vence {formatoFecha(i.fechaVencimiento)}</span>
                      <span className={i.vigente ? "text-green-700 dark:text-green-300" : "text-red-700 dark:text-red-300"}>
                        {i.vigente ? "Vigente" : "Vencida"}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </>
        )}
      </main>
    </div>
  );
}

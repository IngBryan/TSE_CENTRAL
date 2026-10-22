import { randomUUID } from "node:crypto";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Encabezado } from "@/components/encabezado";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import type { Vehiculo } from "./actions";
import { EstadoItv, EstadoPermiso, Habilitacion } from "./estado";
import { NuevoVehiculo } from "./nuevo-vehiculo";

// ABM de Vehiculos: vehiculos de la empresa del responsable (el backend decide si puede verlos).
export default async function Vehiculos() {
  const sesion = await getSesion();
  if (!sesion) redirect("/");

  let vehiculos: Vehiculo[] | null = null;
  let error: string | null = null;
  try {
    const res = await llamarBackend<Vehiculo[] & { detalle?: string }>("/empresa/vehiculos", {
      correlationId: randomUUID(),
      usuarioCi: sesion.perfil.cedula,
    });
    if (res.status === 200) vehiculos = res.data;
    else if (res.status === 403) error = `No tenés acceso a los vehículos: ${res.data?.detalle ?? "no sos responsable de una empresa"}.`;
    else error = "No se pudo obtener el listado de vehículos.";
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    error = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";
  }
  const empresa = sesion.perfil.roles?.find((r) => r.rol === "RESPONSABLE")?.empresa;

  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-6 px-4 py-12">
        <div className="flex flex-col gap-1">
          <Link href="/mi-cuenta" className="text-sm text-zinc-500 hover:underline">
            ← Mi cuenta
          </Link>
          <h1 className="text-3xl font-semibold tracking-tight text-black dark:text-zinc-50">
            Vehículos{empresa ? ` de ${empresa}` : ""}
          </h1>
        </div>

        {error ? (
          <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
            {error}
          </p>
        ) : (
          <>
            <NuevoVehiculo />

            {vehiculos && vehiculos.length > 0 ? (
              <div className="overflow-x-auto rounded-2xl bg-white shadow-sm dark:bg-zinc-950">
                <table className="w-full text-left text-sm">
                  <thead className="border-b border-black/5 text-zinc-500 dark:border-white/10">
                    <tr>
                      <th className="px-4 py-3 font-medium">Matrícula</th>
                      <th className="px-4 py-3 font-medium">Vehículo</th>
                      <th className="px-4 py-3 text-right font-medium">Peso / capacidad</th>
                      <th className="px-4 py-3 font-medium">Permiso</th>
                      <th className="px-4 py-3 font-medium">ITV</th>
                      <th className="px-4 py-3 font-medium">Estado</th>
                    </tr>
                  </thead>
                  <tbody>
                    {vehiculos.map((v) => (
                      <tr
                        key={v.matricula}
                        className={`border-b border-black/5 align-top last:border-0 dark:border-white/10 ${v.activo ? "" : "text-zinc-500"}`}
                      >
                        <td className="px-4 py-3 font-mono">
                          <Link href={`/empresa/vehiculos/${encodeURIComponent(v.matricula)}`} className="underline">
                            {v.matricula}
                          </Link>
                        </td>
                        <td className="px-4 py-3">
                          {v.marca} {v.modelo}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums">
                          {v.peso.toLocaleString("es-UY")} kg
                          <span className="block text-xs text-zinc-500">carga {v.capacidadCargaKg.toLocaleString("es-UY")} kg</span>
                        </td>
                        <td className="px-4 py-3">
                          <EstadoPermiso vehiculo={v} />
                        </td>
                        <td className="px-4 py-3">
                          <EstadoItv vehiculo={v} />
                        </td>
                        <td className="px-4 py-3">
                          <Habilitacion vehiculo={v} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="text-zinc-600 dark:text-zinc-400">Todavía no hay vehículos en la empresa.</p>
            )}
          </>
        )}
      </main>
    </div>
  );
}

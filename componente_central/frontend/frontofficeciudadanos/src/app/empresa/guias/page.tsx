import { randomUUID } from "node:crypto";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Encabezado, botonSecundario } from "@/components/encabezado";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import { obtenerCatalogos, type Guia } from "./actions";
import { NuevaGuia } from "./nueva-guia";

function formatoFecha(iso: string) {
  const [a, m, d] = iso.split("-");
  return `${d}/${m}/${a}`;
}

// Estados despues de la asignacion; EN_CURSO y FINALIZADA los registra el chofer desde la app movil
const ESTADOS: Record<Exclude<Guia["estado"], "SIN_ASIGNAR">, { texto: string; clase: string }> = {
  ASIGNADA: { texto: "Asignada", clase: "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300" },
  EN_CURSO: { texto: "En curso", clase: "bg-blue-50 text-blue-800 dark:bg-blue-950 dark:text-blue-200" },
  FINALIZADA: { texto: "Finalizada", clase: "bg-green-50 text-green-800 dark:bg-green-950 dark:text-green-200" },
  CANCELADA: { texto: "Cancelada", clase: "bg-red-50 text-red-800 dark:bg-red-950 dark:text-red-200" },
};

const nombre = (c?:{ primerNombre?: string; primerApellido?: string } | null) =>
  [c?.primerNombre, c?.primerApellido].filter(Boolean).join(" ");

// Gestion de Viajes de Carga: guias de la empresa del responsable (el backend decide si puede verlas).
export default async function Guias({ searchParams }: PageProps<"/empresa/guias">) {
  const sesion = await getSesion();
  if (!sesion) redirect("/");
  const params = await searchParams;

  let guias: Guia[] | null = null;
  let error: string | null = null;
  try {
    const res = await llamarBackend<Guia[] & { detalle?: string }>("/empresa/guias", {
      correlationId: randomUUID(),
      usuarioCi: sesion.perfil.cedula,
    });
    if (res.status === 200) guias = res.data;
    else if (res.status === 403) error = `No tenés acceso a las guías: ${res.data?.detalle ?? "no sos responsable de una empresa"}.`;
    else error = "No se pudo obtener el listado de guías.";
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    error = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";
  }
  const catalogos = error ? { rubros: [], tiposCarga: [] } : await obtenerCatalogos();
  const empresa = sesion.perfil.roles?.find((r) => r.rol === "RESPONSABLE")?.empresa;
  const hoy = new Date().toLocaleDateString("sv-SE", { timeZone: "America/Montevideo" }); // yyyy-mm-dd

  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-6 px-4 py-12">
        <div className="flex flex-col gap-1">
          <Link href="/mi-cuenta" className="text-sm text-zinc-500 hover:underline">
            ← Mi cuenta
          </Link>
          <h1 className="text-3xl font-semibold tracking-tight text-black dark:text-zinc-50">
            Guías de viaje{empresa ? ` de ${empresa}` : ""}
          </h1>
        </div>

        {typeof params.asignada === "string" && (
          <p role="status" className="rounded-lg bg-green-50 p-4 text-green-900 dark:bg-green-950 dark:text-green-200">
            Listo: la guía {params.asignada} quedó asignada.
          </p>
        )}

        {error ? (
          <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
            {error}
          </p>
        ) : (
          <>
            <NuevaGuia hoy={hoy} catalogos={catalogos} />

            {guias && guias.length > 0 ? (
              <div className="overflow-x-auto rounded-2xl bg-white shadow-sm dark:bg-zinc-950">
                <table className="w-full text-left text-sm">
                  <thead className="border-b border-black/5 text-zinc-500 dark:border-white/10">
                    <tr>
                      <th className="px-4 py-3 font-medium">Guía</th>
                      <th className="px-4 py-3 font-medium">Fecha</th>
                      <th className="px-4 py-3 font-medium">Rubro / tipo de carga</th>
                      <th className="px-4 py-3 font-medium">Recorrido</th>
                      <th className="px-4 py-3 text-right font-medium">Carga</th>
                      <th className="px-4 py-3 font-medium">Asignación</th>
                    </tr>
                  </thead>
                  <tbody>
                    {guias.map((g) => (
                      <tr key={g.id} className="border-b border-black/5 align-top last:border-0 dark:border-white/10">
                        <td className="px-4 py-3 font-mono text-xs">
                          {g.estado === "SIN_ASIGNAR" ? (
                            g.id
                          ) : (
                            <Link href={`/empresa/guias/${encodeURIComponent(g.id)}`} className="underline" title="Ver eventos del viaje">
                              {g.id}
                            </Link>
                          )}
                        </td>
                        <td className="px-4 py-3 tabular-nums">{formatoFecha(g.fecha)}</td>
                        <td className="px-4 py-3">
                          {g.rubroCliente}
                          {g.tipoCarga && <span className="block text-xs text-zinc-500">{g.tipoCarga}</span>}
                        </td>
                        <td className="px-4 py-3">
                          {g.origen} → {g.destino}
                        </td>
                        <td className="px-4 py-3 text-right tabular-nums">{g.pesoCargaKg.toLocaleString("es-UY")} kg</td>
                        <td className="px-4 py-3">
                          {g.estado !== "SIN_ASIGNAR" ? (
                            <span className="flex flex-col gap-1">
                              <span>
                                {g.vehiculo?.matricula} · {nombre(g.chofer) || g.chofer?.cedula}
                              </span>
                              <span className={`w-fit rounded-full px-2 py-0.5 text-xs font-medium ${ESTADOS[g.estado].clase}`}>
                                {ESTADOS[g.estado].texto}
                              </span>
                            </span>
                          ) : (
                            <Link href={`/empresa/guias/${encodeURIComponent(g.id)}/asignar`} className={`${botonSecundario} h-8 px-3`}>
                              Asignar viaje
                            </Link>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="text-zinc-600 dark:text-zinc-400">Todavía no hay guías de viaje.</p>
            )}
          </>
        )}
      </main>
    </div>
  );
}

import { randomUUID } from "node:crypto";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Encabezado } from "@/components/encabezado";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import { ESTADOS, fecha, fechaHora, tipoInfraccion, type InfraccionDetalle } from "@/lib/infracciones";
import { PresentarDescargo } from "./presentar-descargo";

const tarjeta = "rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950";
const titulo = "text-lg font-semibold text-black dark:text-zinc-50";

// Detalle de una infraccion: la evidencia que la origino, el descargo (o el formulario para presentarlo
// dentro del plazo) y la resolucion del funcionario. Abrirlo marca la notificacion como vista.
export default async function Infraccion({ params }: PageProps<"/empresa/infracciones/[id]">) {
  const sesion = await getSesion();
  if (!sesion) redirect("/");
  const { id } = await params;
  const idCaso = /^\d{1,9}$/.test(id) ? Number(id) : null;

  let detalle: InfraccionDetalle | null = null;
  let error: string | null = null;
  if (idCaso === null) {
    error = "La infracción no existe.";
  } else {
    try {
      const res = await llamarBackend<InfraccionDetalle & { detalle?: string }>(`/empresa/infracciones/${idCaso}`, {
        correlationId: randomUUID(),
        usuarioCi: sesion.perfil.cedula,
      });
      if (res.status === 200) detalle = res.data;
      else if (res.status === 404) error = "La infracción no existe.";
      else if (res.status === 403) error = `No tenés acceso a las infracciones: ${res.data?.detalle ?? "no sos responsable de una empresa"}.`;
      else error = "No se pudo obtener la infracción.";
    } catch (e) {
      if (!(e instanceof BackendNoDisponible)) throw e;
      error = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";
    }
  }

  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-6 px-4 py-12">
        <Link href="/empresa/infracciones" className="text-sm text-zinc-500 hover:underline">
          ← Infracciones
        </Link>

        {error || !detalle ? (
          <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
            {error}
          </p>
        ) : (
          <Contenido detalle={detalle} />
        )}
      </main>
    </div>
  );
}

function Contenido({ detalle }: { detalle: InfraccionDetalle }) {
  const { infraccion: i, guia, vehiculo, pesajes, descargo, resolucion } = detalle;
  return (
    <>
      <div className="flex flex-col gap-2">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-3xl font-semibold tracking-tight text-black dark:text-zinc-50">
            {tipoInfraccion(i.tipoCaso)} <span className="text-zinc-400">n.º {i.id}</span>
          </h1>
          <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${ESTADOS[i.estado].color}`}>
            {ESTADOS[i.estado].texto}
          </span>
        </div>
        {i.descripcion && <p className="text-zinc-700 dark:text-zinc-300">{i.descripcion}</p>}
        <p className="text-sm text-zinc-500">Detectada el {fechaHora(i.fechaDeteccion)}</p>
      </div>

      <section className="flex flex-col gap-3">
        <h2 className={titulo}>Evidencia</h2>
        <div className={`grid gap-4 sm:grid-cols-2 ${tarjeta}`}>
          <div className="flex flex-col gap-1 text-sm">
            <p className="text-xs uppercase tracking-wide text-zinc-500">Vehículo</p>
            {vehiculo ? (
              <>
                <p className="font-mono">{vehiculo.matricula}</p>
                <p>
                  {vehiculo.marca} {vehiculo.modelo}
                </p>
                <p className="text-zinc-600 dark:text-zinc-400">
                  Peso {vehiculo.pesoKg.toLocaleString("es-UY")} kg + capacidad declarada{" "}
                  {vehiculo.capacidadCargaKg.toLocaleString("es-UY")} kg ={" "}
                  <strong>máximo {vehiculo.pesoMaximoKg.toLocaleString("es-UY")} kg</strong>
                </p>
              </>
            ) : (
              <p>-</p>
            )}
          </div>
          <div className="flex flex-col gap-1 text-sm">
            <p className="text-xs uppercase tracking-wide text-zinc-500">Guía</p>
            {guia ? (
              <>
                <p className="font-mono">{guia.id}</p>
                <p>
                  {guia.origen ?? "-"} → {guia.destino ?? "-"} ({fecha(guia.fecha)})
                </p>
                <p className="text-zinc-600 dark:text-zinc-400">
                  {guia.rubroCliente ?? "-"} · carga declarada {guia.pesoCargaKg.toLocaleString("es-UY")} kg
                </p>
              </>
            ) : (
              <p className="text-zinc-600 dark:text-zinc-400">Sin guía asociada.</p>
            )}
          </div>
        </div>

        {pesajes.length > 0 && (
          <div className="overflow-x-auto rounded-2xl bg-white shadow-sm dark:bg-zinc-950">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-black/5 text-zinc-500 dark:border-white/10">
                <tr>
                  <th className="px-4 py-3 font-medium">Pesaje</th>
                  <th className="px-4 py-3 font-medium">Balanza</th>
                  <th className="px-4 py-3 text-right font-medium">Peso registrado</th>
                  <th className="px-4 py-3 text-right font-medium">Exceso</th>
                </tr>
              </thead>
              <tbody>
                {pesajes.map((p, n) => (
                  <tr key={n} className="border-b border-black/5 last:border-0 dark:border-white/10">
                    <td className="whitespace-nowrap px-4 py-3">{fechaHora(p.fechaHora)}</td>
                    <td className="px-4 py-3">{p.balanza ?? "-"}</td>
                    <td className="px-4 py-3 text-right tabular-nums">{p.pesoKg.toLocaleString("es-UY")} kg</td>
                    <td className={`px-4 py-3 text-right tabular-nums ${p.excesoKg > 0 ? "font-medium text-red-700 dark:text-red-300" : ""}`}>
                      {p.excesoKg > 0 ? `${p.excesoKg.toLocaleString("es-UY")} kg` : "-"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {detalle.eventosTracking > 0 && (
          <p className="text-sm text-zinc-600 dark:text-zinc-400">
            {detalle.eventosTracking === 1
              ? "1 posición reportada por el sistema de tracking."
              : `${detalle.eventosTracking} posiciones reportadas por el sistema de tracking.`}
          </p>
        )}
      </section>

      <section className="flex flex-col gap-3">
        {descargo ? (
          <>
            <h2 className={titulo}>Descargo presentado</h2>
            <div className={`flex flex-col gap-2 ${tarjeta}`}>
              <p className="whitespace-pre-line">{descargo.descripcion}</p>
              {descargo.fechaPresentacion && (
                <p className="text-xs text-zinc-500">
                  Presentado el {fechaHora(descargo.fechaPresentacion)}
                  {descargo.ciPresentante ? ` por C.I. ${descargo.ciPresentante}` : ""}
                </p>
              )}
            </div>
          </>
        ) : i.admiteDescargo && i.venceDescargo ? (
          <PresentarDescargo idCaso={i.id} vence={fecha(i.venceDescargo)} />
        ) : (
          <>
            <h2 className={titulo}>Descargo</h2>
            <p className="rounded-lg bg-zinc-100 p-4 text-sm text-zinc-700 dark:bg-zinc-900 dark:text-zinc-300">
              {i.estado === "NOTIFICADA"
                ? `Venció el plazo de ${detalle.plazoDescargoDias} días para presentar el descargo.`
                : "La empresa no presentó un descargo para esta infracción."}
            </p>
          </>
        )}
      </section>

      {resolucion && (
        <section className="flex flex-col gap-3">
          <h2 className={titulo}>Resolución del MTOP</h2>
          <div className={`flex flex-col gap-2 ${tarjeta}`}>
            <p className="font-medium">
              {resolucion.decision === "CONFIRMADA" ? "Infracción confirmada" : "Infracción desestimada"}
            </p>
            {resolucion.fundamento && <p className="whitespace-pre-line">{resolucion.fundamento}</p>}
            {resolucion.fechaHora && <p className="text-xs text-zinc-500">Resuelta el {fechaHora(resolucion.fechaHora)}</p>}
          </div>
        </section>
      )}
    </>
  );
}

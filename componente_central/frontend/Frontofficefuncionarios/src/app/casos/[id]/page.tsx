import { randomUUID } from "node:crypto";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { Encabezado } from "@/components/encabezado";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend, type RespuestaBackend } from "@/lib/backend";
import {
  ACCIONES,
  COLOR_ESTADO,
  DECISIONES,
  estado,
  fecha,
  fechaHora,
  kg,
  tipoCaso,
  type CasoDetalle,
  type DescargoVista,
  type ErrorBackend,
  type EstadoCaso,
  type EvidenciaVista,
  type CirculacionVista,
} from "@/lib/casos";
import { ResolverCaso } from "./resolver-caso";
import { EvidenciasEnVivo } from "./evidencias-en-vivo";

const NO_DISPONIBLE = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";

const tarjeta = "rounded-2xl bg-white p-6 text-sm shadow-sm dark:bg-zinc-950";
const titulo = "text-lg font-semibold text-black dark:text-zinc-50";
const dato = "text-zinc-500";

// Ciclo de vida de la letra (seccion 3.5); el ultimo paso es CONFIRMADA o DESESTIMADA
const PASOS: EstadoCaso[] = ["DETECTADA", "NOTIFICADA", "EN_DESCARGO"];

// CU Gestionar casos de fiscalizacion: detalle del caso (estado, empresa, guia), la evidencia que lo
// origino (Flujo Alt 2), el descargo de la empresa (Flujo Alt 3 y 4), la resolucion (FA5) y la pista
// de auditoria. Que quien consulta sea funcionario lo controla el backend (403 si no).
export default async function DetalleCaso({ params }: PageProps<"/casos/[id]">) {
  const sesion = await getSesion();
  if (!sesion) redirect("/");
  const { id } = await params;
  if (!/^\d+$/.test(id)) notFound();

  const correlationId = randomUUID();
  const opciones = { correlationId, usuarioCi: sesion.usuario.cedula };
  let detalle: RespuestaBackend<CasoDetalle & ErrorBackend>;
  let evidencia: RespuestaBackend<EvidenciaVista & ErrorBackend> | null;
  let descargo: RespuestaBackend<DescargoVista & ErrorBackend> | null;
  let circulacion: RespuestaBackend<CirculacionVista & ErrorBackend> | null = null;
  try {
    // En paralelo (p95 < 500 ms). Si el detalle falla, las otras dos fallan por lo mismo y no se muestran.
    // Evidencia y descargo no cortan la pagina si el backend se cae a mitad: se informa en su seccion.
    const opcional = <T,>(p: Promise<T>) => p.catch((e) => (e instanceof BackendNoDisponible ? null : Promise.reject(e)));
    [detalle, evidencia, descargo] = await Promise.all([
      llamarBackend<CasoDetalle & ErrorBackend>(`/casos/${id}`, opciones),
      opcional(llamarBackend<EvidenciaVista & ErrorBackend>(`/casos/${id}/evidencia`, opciones)),
      opcional(llamarBackend<DescargoVista & ErrorBackend>(`/casos/${id}/descargo`, opciones)),
    ]);
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return <Pagina sesion={sesion} id={id} error={NO_DISPONIBLE} />;
  }
  console.info(
    `[casos] correlationId=${correlationId} GET /casos/${id} status=${detalle.status} evidencia=${evidencia?.status} descargo=${descargo?.status}`,
  );

  if (detalle.status === 401) {
    // Error de configuracion, no del usuario: la X-Api-Key no coincide con la de WildFly
    console.error(`[casos] correlationId=${correlationId} el backend rechazo la X-Api-Key: revisar BACKEND_API_KEY`);
  }
  if (detalle.status !== 200 || !detalle.data) {
    const error =
      detalle.status === 404
        ? "El caso de fiscalización no existe."
        : detalle.status === 403
          ? "No tenés acceso a los casos de fiscalización: no estás registrado como funcionario del MTOP."
          : "No se pudo obtener el caso.";
    return <Pagina sesion={sesion} id={id} error={error} />;
  }

  const caso = detalle.data;
  if (caso.tipoCaso === "VIAJE_SIN_GUIA") {
    try {
      circulacion = await llamarBackend<CirculacionVista & ErrorBackend>(`/casos/${id}/circulacion`, opciones);
    } catch (e) {
      if (!(e instanceof BackendNoDisponible)) throw e;
      // Sin MongoDB/servicio de circulación, el resto del expediente sigue visible.
    }
  }
  const ev = evidencia?.status === 200 ? evidencia.data : null;
  const desc = descargo?.status === 200 ? descargo.data : null;
  const sinDescargo = descargo?.status === 404 && descargo.data?.error === "SIN_DESCARGO";
  // Dia de la deteccion en Uruguay (AAAA-MM-DD), para marcar habilitaciones vencidas a esa fecha
  const diaDeteccion = caso.fechaDeteccion
    ? new Date(caso.fechaDeteccion).toLocaleDateString("sv-SE", { timeZone: "America/Montevideo" })
    : null;
  const vencida = (venc: string | null) => venc != null && diaDeteccion != null && venc < diaDeteccion;
  const posterior = (emision: string | null) => emision != null && diaDeteccion != null && emision > diaDeteccion;

  return (
    <Pagina sesion={sesion} id={id} estado={caso.estado} subtitulo={tipoCaso(caso.tipoCaso)}>
      <section className={`grid gap-x-6 gap-y-2 sm:grid-cols-2 ${tarjeta}`}>
        <p><span className={dato}>Detectado:</span> {fechaHora(caso.fechaDeteccion)}</p>
        <p>
          <span className={dato}>Empresa:</span> {caso.empresa ?? "-"}
          {caso.nroEmpresa != null && <span className="text-zinc-500"> (N° {caso.nroEmpresa})</span>}
        </p>
        <p><span className={dato}>Guía:</span> <span className="font-mono">{caso.guia?.id ?? "Sin guía"}</span></p>
        <p><span className={dato}>Vehículo:</span> <span className="font-mono">{caso.matricula ?? "-"}</span></p>
        {caso.descripcion && (
          <p className="sm:col-span-2"><span className={dato}>Descripción:</span> {caso.descripcion}</p>
        )}
      </section>

      <CicloDeVida actual={caso.estado} />

      {caso.resolucion ? (
        <section className={`flex flex-col gap-2 ${tarjeta}`}>
          <h2 className={titulo}>Resolución</h2>
          <p>
            <span className="font-medium">{DECISIONES[caso.resolucion.decision] ?? caso.resolucion.decision}</span>
            <span className="text-zinc-500">
              {" "}· {fechaHora(caso.resolucion.fechaHora)} · funcionario C.I. {caso.resolucion.ciFuncionario}
            </span>
          </p>
          <p className="whitespace-pre-line">{caso.resolucion.fundamento}</p>
        </section>
      ) : caso.puedeResolverse ? (
        <ResolverCaso idCaso={caso.id} sinDescargo={sinDescargo} />
      ) : (
        <p className="rounded-lg bg-zinc-100 p-4 text-sm text-zinc-700 dark:bg-zinc-900 dark:text-zinc-300">
          El caso todavía no se puede resolver: se resuelve cuando está en descargo. Estado actual: {estado(caso.estado)}.
        </p>
      )}

      <section className="flex flex-col gap-3">
        <h2 className={titulo}>Evidencia</h2>
        {!ev ? (
          <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
            {evidencia ? "No se pudo obtener la evidencia del caso." : NO_DISPONIBLE}
          </p>
        ) : (
          <>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className={`flex flex-col gap-1 ${tarjeta}`}>
                <h3 className="font-medium text-black dark:text-zinc-50">Guía de viaje</h3>
                {ev.guia ? (
                  <>
                    <p><span className={dato}>Fecha:</span> {fecha(ev.guia.fecha)}</p>
                    <p><span className={dato}>Recorrido:</span> {ev.guia.origen ?? "-"} → {ev.guia.destino ?? "-"}</p>
                    <p>
                      <span className={dato}>Carga declarada:</span> {kg(ev.guia.pesoCargaKg)}
                      {ev.guia.tipoCarga ? ` · ${ev.guia.tipoCarga}` : ""}
                    </p>
                    <p><span className={dato}>Rubro:</span> {ev.guia.rubroCliente ?? "-"}</p>
                    <p><span className={dato}>Estado de la guía:</span> {ev.guia.estado ?? "-"}</p>
                  </>
                ) : (
                  <p className="text-zinc-600 dark:text-zinc-400">
                    El vehículo no tenía una guía asignada ni en curso.
                  </p>
                )}
              </div>
              <div className={`flex flex-col gap-1 ${tarjeta}`}>
                <h3 className="font-medium text-black dark:text-zinc-50">Vehículo (datos declarados)</h3>
                {ev.vehiculo ? (
                  <>
                    <p>
                      <span className="font-mono">{ev.vehiculo.matricula}</span>
                      {[ev.vehiculo.marca, ev.vehiculo.modelo].some(Boolean) &&
                        ` · ${[ev.vehiculo.marca, ev.vehiculo.modelo].filter(Boolean).join(" ")}`}
                    </p>
                    <p>
                      <span className={dato}>Peso + capacidad:</span> {kg(ev.vehiculo.pesoKg)} + {kg(ev.vehiculo.capacidadCargaKg)} ={" "}
                      <span className="font-medium">{kg(ev.vehiculo.pesoMaximoKg)}</span>
                    </p>
                    <p>
                      <span className={dato}>Permiso de circulación:</span>{" "}
                      {ev.vehiculo.nroPermiso ? `N° ${ev.vehiculo.nroPermiso}, vence ${fecha(ev.vehiculo.vencimientoPermiso)}` : "sin permiso"}
                      {posterior(ev.vehiculo.emisionPermiso) ? (
                        <span className="ml-2 rounded bg-amber-100 px-1.5 py-0.5 text-xs font-medium text-amber-900 dark:bg-amber-950 dark:text-amber-200">
                          renovado después de la detección ({fecha(ev.vehiculo.emisionPermiso)})
                        </span>
                      ) : (
                        vencida(ev.vehiculo.vencimientoPermiso) && <Vencido />
                      )}
                    </p>
                    <p>
                      <span className={dato}>ITV vigente al detectarse:</span>{" "}
                      {ev.vehiculo.vencimientoItv ? `vence ${fecha(ev.vehiculo.vencimientoItv)}` : "sin ITV"}
                      {vencida(ev.vehiculo.vencimientoItv) && <Vencido />}
                    </p>
                  </>
                ) : (
                  <p className="text-zinc-600 dark:text-zinc-400">La guía no tiene vehículo asignado.</p>
                )}
              </div>
            </div>

            <div className={tarjeta}>
              <h3 className="mb-3 font-medium text-black dark:text-zinc-50">Pesajes</h3>
              {ev.pesajes.length === 0 ? (
                <p className="text-zinc-600 dark:text-zinc-400">El caso no tiene pesajes asociados.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left">
                    <thead className="text-zinc-500">
                      <tr>
                        <th className="py-1 pr-4 font-normal">Fecha y hora</th>
                        <th className="py-1 pr-4 font-normal">Balanza</th>
                        <th className="py-1 pr-4 text-right font-normal">Peso</th>
                        <th className="py-1 text-right font-normal">Exceso</th>
                      </tr>
                    </thead>
                    <tbody>
                      {ev.pesajes.map((p, i) => (
                        <tr key={i} className="border-t border-black/5 dark:border-white/10">
                          <td className="py-2 pr-4">{fechaHora(p.fechaHora)}</td>
                          <td className="py-2 pr-4">{p.balanza ?? "-"}</td>
                          <td className="py-2 pr-4 text-right tabular-nums">{kg(p.pesoKg)}</td>
                          <td
                            className={`py-2 text-right tabular-nums ${
                              p.excesoKg > 0 ? "font-medium text-red-700 dark:text-red-300" : "text-zinc-500"
                            }`}
                          >
                            {p.excesoKg > 0 ? `+${kg(p.excesoKg)}` : "-"}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

          </>
        )}
      </section>

      <EvidenciasEnVivo id={caso.id} tipo={caso.tipoCaso} guiaId={caso.guia?.id ?? null} fechaDeteccion={caso.fechaDeteccion}
        estadoCaso={caso.estado} eventosIniciales={ev?.eventosTracking ?? []}
        pruebasIniciales={ev?.pruebasDesvio ?? []}
        circulacion={circulacion?.status === 200 ? circulacion.data : null} />

      <section className="flex flex-col gap-3">
        <h2 className={titulo}>Descargo de la empresa</h2>
        {desc ? (
          <div className={`flex flex-col gap-1 ${tarjeta}`}>
            {desc.estado && <p className="text-xs uppercase tracking-wide text-zinc-500">{desc.estado.replace(/_/g, " ")}</p>}
            <p className="whitespace-pre-line">{desc.descripcion ?? "-"}</p>
            {desc.fechaPresentacion && (
              <p className={`text-xs ${dato}`}>
                Presentado el {fechaHora(desc.fechaPresentacion)}
                {desc.ciPresentante ? ` por el responsable C.I. ${desc.ciPresentante}` : ""}
              </p>
            )}
          </div>
        ) : sinDescargo ? (
          <p className="rounded-lg bg-zinc-100 p-4 text-sm text-zinc-700 dark:bg-zinc-900 dark:text-zinc-300">
            La empresa no presentó un descargo para este caso.
          </p>
        ) : (
          <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
            {descargo ? "No se pudo obtener el descargo." : NO_DISPONIBLE}
          </p>
        )}
      </section>

      <section className="flex flex-col gap-3">
        <h2 className={titulo}>Historial del caso</h2>
        {caso.historial.length === 0 ? (
          <p className="text-zinc-600 dark:text-zinc-400">No hay registros de auditoría para este caso.</p>
        ) : (
          <ol className="flex flex-col gap-3">
            {caso.historial.map((r, i) => (
              <li
                key={`${r.fechaHora}-${i}`}
                className={`rounded-xl bg-white p-4 text-sm shadow-sm dark:bg-zinc-950 ${
                  r.accion === "RESOLUCION" ? "border-l-4 border-[#0068b3]" : ""
                }`}
              >
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <span className="font-medium text-black dark:text-zinc-50">{ACCIONES[r.accion] ?? r.accion}</span>
                  <span className="text-xs text-zinc-500">
                    {fechaHora(r.fechaHora)} · {r.usuario}
                  </span>
                </div>
                <p className="mt-1 text-zinc-600 dark:text-zinc-400">
                  {r.estadoAnterior ? `${estado(r.estadoAnterior)} → ` : ""}
                  {estado(r.estadoNuevo)}
                </p>
                {r.fundamento && <p className="mt-1 whitespace-pre-line">{r.fundamento}</p>}
              </li>
            ))}
          </ol>
        )}
      </section>
    </Pagina>
  );
}

function Pagina({
  sesion,
  id,
  estado: actual,
  subtitulo,
  error,
  children,
}: {
  sesion: NonNullable<Awaited<ReturnType<typeof getSesion>>>;
  id: string;
  estado?: EstadoCaso | null;
  subtitulo?: string;
  error?: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 px-4 py-12">
        <div className="flex flex-col gap-1">
          <Link href="/casos" className="text-sm text-zinc-500 hover:underline">
            ← Casos de fiscalización
          </Link>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="text-3xl font-semibold tracking-tight text-black dark:text-zinc-50">Caso N° {id}</h1>
            {actual && (
              <span className={`rounded-full px-3 py-1 text-sm font-medium ${COLOR_ESTADO[actual] ?? ""}`}>
                {estado(actual)}
              </span>
            )}
          </div>
          {subtitulo && <p className="text-lg text-zinc-700 dark:text-zinc-300">{subtitulo}</p>}
        </div>

        {error ? (
          <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
            {error}
          </p>
        ) : (
          children
        )}
      </main>
    </div>
  );
}

// detectada -> notificada -> en descargo -> confirmada | desestimada
function CicloDeVida({ actual }: { actual: EstadoCaso | null }) {
  const final: EstadoCaso = actual === "DESESTIMADA" ? "DESESTIMADA" : "CONFIRMADA";
  const pasos = [...PASOS, final];
  const indice = actual ? pasos.indexOf(actual) : -1;
  return (
    <ol className="flex flex-wrap items-center gap-2 text-sm" aria-label="Ciclo de vida del caso">
      {pasos.map((p, i) => (
        <li key={p} className="flex items-center gap-2">
          <span
            aria-current={i === indice ? "step" : undefined}
            className={`rounded-full px-3 py-1 ${
              i === indice
                ? `font-medium ${COLOR_ESTADO[p]}`
                : i < indice
                  ? "text-zinc-700 dark:text-zinc-300"
                  : "text-zinc-400 dark:text-zinc-600"
            }`}
          >
            {i === pasos.length - 1 && indice < i ? "Confirmada / Desestimada" : estado(p)}
          </span>
          {i < pasos.length - 1 && <span className="text-zinc-400">→</span>}
        </li>
      ))}
    </ol>
  );
}

function Vencido() {
  return (
    <span className="ml-2 rounded bg-red-100 px-1.5 py-0.5 text-xs font-medium text-red-800 dark:bg-red-950 dark:text-red-200">
      vencido al detectarse
    </span>
  );
}

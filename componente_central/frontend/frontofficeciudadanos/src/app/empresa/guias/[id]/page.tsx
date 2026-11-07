import { randomUUID } from "node:crypto";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Encabezado } from "@/components/encabezado";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import { BASE_PATH } from "@/lib/base-path";
import { obtenerCatalogos, type ChoferResumen, type Guia, type VehiculoResumen } from "../actions";
import { ModificarGuia } from "./modificar-guia";
import { mostrarLocalidad, type LocalidadVista } from "@/lib/localidades";
import type { RutaAsignada } from "./rutas/tipos-rutas";

type EventoViaje = {
  idEvento: string;
  tipo: "INICIO" | "FIN" | "CARGA_PARCIAL" | "DESCARGA_PARCIAL" | "INCIDENTE";
  fechaHoraDispositivo: string;
  fechaRecepcion: string;
  chofer?: ChoferResumen | null;
  descripcion?: string | null;
  latitud?: number | null;
  longitud?: number | null;
  conFoto: boolean;
  resultado: "APLICADO" | "SIN_EFECTO" | "CONFLICTO";
  codigo?: string | null;
  detalle?: string | null;
};

// Cambio hecho por el responsable desde el frontoffice (quien, cuando, que cambio)
type ModificacionGuia = {
  tipo: "EDICION" | "REASIGNACION" | "CANCELACION";
  fecha: string;
  ciResponsable: string;
  cambios?: string | null;
  motivo?: string | null;
  versionAnterior?: number | null;
  versionNueva?: number | null;
};

type DetalleGuia = {
  guia: Guia;
  eventos: EventoViaje[];
  modificaciones: ModificacionGuia[];
  vehiculos: VehiculoResumen[];
  choferes: ChoferResumen[];
};

const MODIFICACIONES: Record<ModificacionGuia["tipo"], string> = {
  EDICION: "La empresa editó la guía",
  REASIGNACION: "La empresa reasignó la guía",
  CANCELACION: "La empresa canceló la guía",
};

const TIPOS: Record<EventoViaje["tipo"], string> = {
  INICIO: "Inicio del viaje",
  FIN: "Fin del viaje",
  CARGA_PARCIAL: "Carga parcial",
  DESCARGA_PARCIAL: "Descarga parcial",
  INCIDENTE: "Incidente",
};

const ESTADOS: Record<string, string> = {
  SIN_ASIGNAR: "Sin asignar",
  ASIGNADA: "Asignada",
  EN_CURSO: "En curso",
  FINALIZADA: "Finalizada",
  CANCELADA: "Cancelada",
};

const hora = (iso: string) =>
  new Date(iso).toLocaleString("es-UY", { timeZone: "America/Montevideo", dateStyle: "short", timeStyle: "short" });

// Detalle de una guia con el historial de eventos que registro el chofer desde la app movil. Los
// eventos que el central no pudo aplicar (politica de conflictos) se muestran marcados.
export default async function DetalleGuia({ params }: PageProps<"/empresa/guias/[id]">) {
  const sesion = await getSesion();
  if (!sesion) redirect("/");
  const { id } = await params;
  // Los catalogos de tipos de carga y rubros, por si se edita la guia
  const pedidoCatalogos = obtenerCatalogos();

  let datos: DetalleGuia | null = null;
  let error: string | null = null;
  let rutasAutorizadas: RutaAsignada[] = [];
  let errorRutas = false;
  let localidades: LocalidadVista[] = [];
  try {
    const res = await llamarBackend<DetalleGuia & { detalle?: string }>(
      `/empresa/guias/${encodeURIComponent(id)}/eventos`,
      { correlationId: randomUUID(), usuarioCi: sesion.perfil.cedula },
    );
    if (res.status === 200) datos = res.data;
    else if (res.status === 404) error = "La guía no existe.";
    else if (res.status === 403) error = `No tenés acceso a las guías: ${res.data?.detalle ?? "no sos responsable de una empresa"}.`;
    else error = "No se pudo obtener la guía.";
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    error = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";
  }
  // Las rutas son información complementaria: un fallo de consulta no oculta la guía.
  if (datos) {
    try {
      const resultado = await llamarBackend<RutaAsignada[]>(
        `/empresa/guias/${encodeURIComponent(id)}/rutas`,
        { correlationId: randomUUID(), usuarioCi: sesion.perfil.cedula },
      );
      if (resultado.status === 200 && Array.isArray(resultado.data)) rutasAutorizadas = resultado.data;
      else errorRutas = true;
    } catch (e) {
      if (!(e instanceof BackendNoDisponible)) throw e;
      errorRutas = true;
    }
  }
  if (datos?.guia.estado === "ASIGNADA") {
    try {
      const res = await llamarBackend<LocalidadVista[]>("/localidades", {
        correlationId: randomUUID(), usuarioCi: sesion.perfil.cedula,
      });
      if (res.status === 200 && Array.isArray(res.data)) localidades = res.data;
    } catch (e) {
      if (!(e instanceof BackendNoDisponible)) throw e;
    }
  }
  // Linea de tiempo: eventos del chofer (por la hora en que ocurrieron) y modificaciones de la empresa
  const linea = datos
    ? [
        ...datos.eventos.map((e) => ({ fecha: e.fechaHoraDispositivo, evento: e, modificacion: null })),
        ...(datos.modificaciones ?? []).map((m) => ({ fecha: m.fecha, evento: null, modificacion: m })),
      ].sort((a, b) => new Date(a.fecha).getTime() - new Date(b.fecha).getTime())
    : [];
  const hoy = new Date().toLocaleDateString("sv-SE", { timeZone: "America/Montevideo" });
  const conflictos = datos?.eventos.filter((e) => e.resultado === "CONFLICTO").length ?? 0;
  const { tiposCarga, rubros } = await pedidoCatalogos;

  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 px-4 py-12">
        <div className="flex flex-col gap-1">
          <Link href="/empresa/guias" className="text-sm text-zinc-500 hover:underline">
            ← Guías de viaje
          </Link>
          <h1 className="text-3xl font-semibold tracking-tight text-black dark:text-zinc-50">
            Guía <span className="font-mono">{id}</span>
          </h1>
        </div>

        {error || !datos ? (
          <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
            {error}
          </p>
        ) : (
          <>
            <section className="grid gap-x-6 gap-y-2 rounded-2xl bg-white p-6 text-sm shadow-sm sm:grid-cols-2 dark:bg-zinc-950">
              <p><span className="text-zinc-500">Estado:</span> {ESTADOS[datos.guia.estado] ?? datos.guia.estado}</p>
              <p><span className="text-zinc-500">Fecha:</span> {datos.guia.fecha.split("-").reverse().join("/")}</p>
              <p><span className="text-zinc-500">Recorrido:</span> {mostrarLocalidad(datos.guia.origen, datos.guia.origenDepartamento)} → {mostrarLocalidad(datos.guia.destino, datos.guia.destinoDepartamento)}</p>
              <p>
                <span className="text-zinc-500">Carga:</span> {datos.guia.pesoCargaKg.toLocaleString("es-UY")} kg
                {datos.guia.tipoCarga ? ` · ${datos.guia.tipoCarga}` : ""}
              </p>
              <p><span className="text-zinc-500">Rubro:</span> {datos.guia.rubroCliente}</p>
              <p>
                <span className="text-zinc-500">Vehículo y chofer:</span> {datos.guia.vehiculo?.matricula ?? "-"} ·{" "}
                {[datos.guia.chofer?.primerNombre, datos.guia.chofer?.primerApellido].filter(Boolean).join(" ") ||
                  datos.guia.chofer?.cedula ||
                  "-"}
              </p>
            </section>

            <section aria-labelledby="rutas-autorizadas-titulo" className="rounded-2xl bg-white p-6 text-sm shadow-sm dark:bg-zinc-950">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <h2 id="rutas-autorizadas-titulo" className="text-lg font-semibold">Rutas autorizadas</h2>
                <Link href={`/empresa/guias/${encodeURIComponent(id)}/rutas`}
                  className="rounded-lg bg-[#0068b3] px-4 py-2 font-medium text-white hover:bg-[#005596]">
                  {datos.guia.estado === "SIN_ASIGNAR" || datos.guia.estado === "ASIGNADA"
                    ? "Consultar o autorizar rutas" : "Ver rutas en el mapa"}
                </Link>
              </div>
              {errorRutas ? (
                <p role="status" className="mt-3 text-amber-800 dark:text-amber-200">
                  No pudimos consultar las rutas de esta guía. Podés volver a intentarlo desde la pantalla de rutas.
                </p>
              ) : rutasAutorizadas.length === 0 ? (
                <p className="mt-3 text-zinc-600 dark:text-zinc-400">Esta guía todavía no tiene rutas autorizadas.</p>
              ) : (
                <ol className="mt-3 grid gap-2 sm:grid-cols-2">
                  {[...rutasAutorizadas].sort((a, b) => a.alternativa - b.alternativa).map(r => (
                    <li key={r.idRuta} className="rounded-lg bg-zinc-50 p-3 dark:bg-zinc-900">
                      <span className="font-medium">Alternativa {r.alternativa}</span>
                      <span className="ml-2 text-zinc-600 dark:text-zinc-400">
                        {(r.distanciaMetros / 1000).toLocaleString("es-UY", { maximumFractionDigits: 1 })} km
                      </span>
                    </li>
                  ))}
                </ol>
              )}
            </section>

            {datos.guia.estado === "ASIGNADA" && (
              <ModificarGuia
                guia={datos.guia}
                vehiculos={datos.vehiculos}
                choferes={datos.choferes}
                tiposCarga={tiposCarga}
                rubros={rubros}
                hoy={hoy}
                localidades={localidades}
              />
            )}

            <section className="flex flex-col gap-3">
              <h2 className="text-lg font-semibold text-black dark:text-zinc-50">Línea de tiempo del viaje</h2>
              {conflictos > 0 && (
                <p role="status" className="rounded-lg bg-amber-50 p-4 text-amber-900 dark:bg-amber-950 dark:text-amber-200">
                  {conflictos === 1 ? "Hay 1 evento" : `Hay ${conflictos} eventos`} que no se pudo aplicar a la guía
                  (registrado en conflicto). Revisalo con el chofer.
                </p>
              )}
              {linea.length === 0 ? (
                <p className="text-zinc-600 dark:text-zinc-400">El chofer todavía no registró eventos.</p>
              ) : (
                <ol className="flex flex-col gap-3">
                  {linea.map(({ evento: e, modificacion: m }) =>
                    m ? (
                      <li
                        key={`mod-${m.fecha}`}
                        className="rounded-xl border-l-4 border-[#0068b3] bg-white p-4 text-sm shadow-sm dark:bg-zinc-950"
                      >
                        <div className="flex flex-wrap items-baseline justify-between gap-2">
                          <span className="font-medium text-black dark:text-zinc-50">{MODIFICACIONES[m.tipo] ?? m.tipo}</span>
                          <span className="text-xs text-zinc-500">{hora(m.fecha)} · responsable C.I. {m.ciResponsable}</span>
                        </div>
                        {m.cambios && <p className="mt-1">{m.cambios}</p>}
                        {m.motivo && <p className="mt-1">Motivo: {m.motivo}</p>}
                        <p className="mt-1 text-xs text-zinc-500">
                          Versión de la guía {m.versionAnterior} → {m.versionNueva}
                        </p>
                      </li>
                    ) : e ? (
                    <li
                      key={e.idEvento}
                      className={`rounded-xl bg-white p-4 text-sm shadow-sm dark:bg-zinc-950 ${
                        e.resultado === "CONFLICTO" ? "border-l-4 border-amber-500" : ""
                      }`}
                    >
                      <div className="flex flex-wrap items-baseline justify-between gap-2">
                        <span className="font-medium text-black dark:text-zinc-50">{TIPOS[e.tipo] ?? e.tipo}</span>
                        <span className="text-xs text-zinc-500">
                          Ocurrió {hora(e.fechaHoraDispositivo)} · recibido {hora(e.fechaRecepcion)}
                        </span>
                      </div>
                      {e.descripcion && <p className="mt-1">{e.descripcion}</p>}
                      <p className="mt-1 text-xs text-zinc-500">
                        Chofer: {[e.chofer?.primerNombre, e.chofer?.primerApellido].filter(Boolean).join(" ") || e.chofer?.cedula}
                        {e.latitud != null && e.longitud != null && (
                          <>
                            {" · "}
                            <a
                              className="underline"
                              href={`https://www.openstreetmap.org/?mlat=${e.latitud}&mlon=${e.longitud}#map=14/${e.latitud}/${e.longitud}`}
                              target="_blank"
                              rel="noreferrer"
                            >
                              ver ubicación
                            </a>
                          </>
                        )}
                      </p>
                      {e.resultado === "CONFLICTO" && (
                        <p className="mt-2 text-amber-800 dark:text-amber-300">
                          En conflicto: {e.detalle ?? e.codigo}. No cambió el estado de la guía.
                        </p>
                      )}
                      {e.resultado === "SIN_EFECTO" && (
                        <p className="mt-2 text-zinc-500">Sin efecto: {e.detalle ?? e.codigo}</p>
                      )}
                      {e.conFoto && (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img
                          src={`${BASE_PATH}/empresa/guias/${encodeURIComponent(id)}/eventos/${encodeURIComponent(e.idEvento)}/foto`}
                          alt={`Foto del ${(TIPOS[e.tipo] ?? e.tipo).toLowerCase()}`}
                          className="mt-3 max-h-80 rounded-lg"
                        />
                      )}
                    </li>
                    ) : null,
                  )}
                </ol>
              )}
            </section>
          </>
        )}
      </main>
    </div>
  );
}

import { randomUUID } from "node:crypto";
import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { Encabezado } from "@/components/encabezado";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend, type RespuestaBackend } from "@/lib/backend";
import { BASE_PATH } from "@/lib/base-path";
import { COLOR_ESTADO, estado, fecha, fechaHora, kg, tipoCaso, type ErrorBackend } from "@/lib/casos";
import {
  COLOR_ESTADO_VIAJE,
  estadoViaje,
  idValido,
  MODIFICACIONES,
  nombreChofer,
  TIPOS_EVENTO,
  type EstadoViaje,
  type EventoVista,
  type ModificacionVista,
  type ViajeDetalle,
} from "@/lib/viajes";
import { registrarLog } from "@/lib/log";

const NO_DISPONIBLE = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";

const tarjeta = "rounded-2xl bg-white p-6 text-sm shadow-sm dark:bg-zinc-950";
const titulo = "text-lg font-semibold text-black dark:text-zinc-50";
const dato = "text-zinc-500";

// Linea de tiempo: eventos del chofer (por la hora del dispositivo) y modificaciones de la empresa
type Entrada = { cuando: string | null; evento?: EventoVista; modificacion?: ModificacionVista };

// CU Monitorear viajes y pesajes: detalle de un viaje asignado, en curso o finalizado con los pesajes del
// vehiculo durante el viaje y la linea de tiempo. Que quien consulta sea funcionario lo controla el backend
// (403 si no); una guia sin asignar o cancelada da 404 como si no existiera.
export default async function DetalleViaje({ params }: PageProps<"/viajes/[id]">) {
  const sesion = await getSesion();
  if (!sesion) redirect("/");
  const { id } = await params;
  if (!idValido(id)) notFound();

  const correlationId = randomUUID();
  const ruta = `/viajes/${encodeURIComponent(id)}`;
  let res: RespuestaBackend<ViajeDetalle & ErrorBackend>;
  try {
    res = await llamarBackend<ViajeDetalle & ErrorBackend>(ruta, { correlationId, usuarioCi: sesion.usuario.cedula });
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return <Pagina sesion={sesion} id={id} error={NO_DISPONIBLE} />;
  }

  if (res.status === 401) {
    // Error de configuracion, no del usuario: la X-Api-Key no coincide con la de WildFly
    registrarLog("error", "viajes", { correlationId }, { detalle: `el backend rechazo la X-Api-Key: revisar BACKEND_API_KEY` });
  }
  if (res.status !== 200 || !res.data) {
    const error =
      res.status === 404
        ? "El viaje no existe o la guía todavía no tiene vehículo y chofer asignados."
        : res.status === 403
          ? "No tenés acceso a los viajes: no estás registrado como funcionario del MTOP."
          : "No se pudo obtener el viaje.";
    return <Pagina sesion={sesion} id={id} error={error} />;
  }

  const v = res.data;
  const conExceso = v.pesajes.filter((p) => p.excesoKg > 0).length;
  const linea: Entrada[] = [
    ...v.modificaciones.map((m) => ({ cuando: m.fecha, modificacion: m })),
    ...v.eventos.map((e) => ({ cuando: e.fechaHoraDispositivo, evento: e })),
  ].sort((a, b) => (a.cuando ? Date.parse(a.cuando) : 0) - (b.cuando ? Date.parse(b.cuando) : 0));

  return (
    <Pagina sesion={sesion} id={id} estado={v.estado} subtitulo={`${v.origen ?? "-"} → ${v.destino ?? "-"}`}>
      <section className={`grid gap-x-6 gap-y-2 sm:grid-cols-2 ${tarjeta}`}>
        <p>
          <span className={dato}>Empresa:</span> {v.empresa ?? "-"}
          {v.nroEmpresa != null && <span className="text-zinc-500"> (N° {v.nroEmpresa})</span>}
        </p>
        <p><span className={dato}>Fecha de la guía:</span> {fecha(v.fecha)}</p>
        <p><span className={dato}>Chofer:</span> {nombreChofer(v.chofer)}{v.chofer?.ci && <span className="text-zinc-500"> (C.I. {v.chofer.ci})</span>}</p>
        <p><span className={dato}>Asignada:</span> {fechaHora(v.fechaAsignacion)}</p>
        <p><span className={dato}>Inicio:</span> {v.inicio ? fechaHora(v.inicio) : v.estado === "ASIGNADA" ? "todavía no empezó" : "-"}</p>
        <p><span className={dato}>Fin:</span> {v.fin ? fechaHora(v.fin) : v.estado === "EN_CURSO" ? "en curso" : "-"}</p>
        <p>
          <span className={dato}>Carga declarada:</span> {kg(v.pesoCargaKg)}
          {v.tipoCarga ? ` · ${v.tipoCarga}` : ""}
        </p>
        <p><span className={dato}>Rubro:</span> {v.rubroCliente ?? "-"}</p>
      </section>

      <section className={`flex flex-col gap-1 ${tarjeta}`}>
        <h2 className={titulo}>Vehículo</h2>
        {v.vehiculo ? (
          <>
            <p>
              <span className="font-mono">{v.vehiculo.matricula}</span>
              {[v.vehiculo.marca, v.vehiculo.modelo].some(Boolean) &&
                ` · ${[v.vehiculo.marca, v.vehiculo.modelo].filter(Boolean).join(" ")}`}
            </p>
            <p>
              <span className={dato}>Peso + capacidad declarada:</span> {kg(v.vehiculo.pesoKg)} + {kg(v.vehiculo.capacidadCargaKg)} ={" "}
              <span className="font-medium">{kg(v.vehiculo.pesoMaximoKg)}</span> máximo
            </p>
          </>
        ) : (
          <p className="text-zinc-600 dark:text-zinc-400">La guía no tiene vehículo asignado.</p>
        )}
      </section>

      <section className="flex flex-col gap-3">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 className={titulo}>Pesajes durante el viaje</h2>
          {conExceso > 0 && (
            <span className="rounded-full bg-red-100 px-2.5 py-0.5 text-xs font-medium text-red-900 dark:bg-red-950 dark:text-red-200">
              {conExceso === 1 ? "1 pesaje con exceso" : `${conExceso} pesajes con exceso`}
            </span>
          )}
        </div>
        <div className={tarjeta}>
          {v.pesajes.length === 0 ? (
            <p className="text-zinc-600 dark:text-zinc-400">
              {v.estado === "ASIGNADA"
                ? "El viaje todavía no empezó."
                : v.estado === "EN_CURSO"
                  ? "El vehículo todavía no pasó por ninguna balanza en este viaje."
                  : "El vehículo no pasó por ninguna balanza durante el viaje."}
            </p>
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
                  {v.pesajes.map((p, i) => (
                    <tr key={`${p.fechaHora}-${i}`} className="border-t border-black/5 dark:border-white/10">
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
      </section>

      <section className="flex flex-col gap-3">
        <h2 className={titulo}>Casos generados</h2>
        <div className={tarjeta}>
          {v.casos.length === 0 ? (
            <p className="text-zinc-600 dark:text-zinc-400">
              {v.estado === "FINALIZADA"
                ? "La fiscalización automática no detectó casos en este viaje."
                : "La fiscalización automática no detectó casos en este viaje hasta ahora."}
            </p>
          ) : (
            <ul className="flex flex-col divide-y divide-black/5 dark:divide-white/10">
              {v.casos.map((c) => (
                <li key={c.id} className="py-2 first:pt-0 last:pb-0">
                  <Link href={`/casos/${c.id}`} className="flex flex-wrap items-center justify-between gap-2 hover:underline">
                    <span>
                      <span className="font-medium text-black dark:text-zinc-50">{tipoCaso(c.tipo)}</span>
                      <span className="text-zinc-500"> · caso N° {c.id} · {fechaHora(c.fechaDeteccion)}</span>
                    </span>
                    <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${COLOR_ESTADO[c.estado] ?? ""}`}>
                      {estado(c.estado)}
                    </span>
                  </Link>
                  {c.descripcion && <p className="mt-1 text-zinc-600 dark:text-zinc-400">{c.descripcion}</p>}
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>

      <section className="flex flex-col gap-3">
        <h2 className={titulo}>Línea de tiempo</h2>
        {linea.length === 0 ? (
          <p className="text-zinc-600 dark:text-zinc-400">El chofer no registró eventos en este viaje.</p>
        ) : (
          <ol className="flex flex-col gap-3">
            {linea.map(({ evento: e, modificacion: m }, i) =>
              m ? (
                <li key={`mod-${m.fecha}-${i}`} className="rounded-xl border-l-4 border-[#0068b3] bg-white p-4 text-sm shadow-sm dark:bg-zinc-950">
                  <div className="flex flex-wrap items-baseline justify-between gap-2">
                    <span className="font-medium text-black dark:text-zinc-50">{MODIFICACIONES[m.tipo] ?? m.tipo}</span>
                    <span className="text-xs text-zinc-500">
                      {fechaHora(m.fecha)} · responsable C.I. {m.ciResponsable}
                    </span>
                  </div>
                  {m.cambios && <p className="mt-1">{m.cambios}</p>}
                  {m.motivo && <p className="mt-1">Motivo: {m.motivo}</p>}
                </li>
              ) : e ? (
                <li
                  key={e.idEvento}
                  className={`rounded-xl bg-white p-4 text-sm shadow-sm dark:bg-zinc-950 ${
                    e.resultado === "CONFLICTO" ? "border-l-4 border-amber-500" : ""
                  }`}
                >
                  <div className="flex flex-wrap items-baseline justify-between gap-2">
                    <span className="font-medium text-black dark:text-zinc-50">{TIPOS_EVENTO[e.tipo] ?? e.tipo}</span>
                    <span className="text-xs text-zinc-500">
                      Ocurrió {fechaHora(e.fechaHoraDispositivo)} · recibido {fechaHora(e.fechaRecepcion)}
                    </span>
                  </div>
                  {e.descripcion && <p className="mt-1 whitespace-pre-line">{e.descripcion}</p>}
                  <p className="mt-1 text-xs text-zinc-500">
                    Chofer: {nombreChofer(e.chofer)}
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
                  {e.resultado === "SIN_EFECTO" && <p className="mt-2 text-zinc-500">Sin efecto: {e.detalle ?? e.codigo}</p>}
                  {e.conFoto && (
                    // La sirve el route handler de esta app (el navegador no tiene la API key): lleva el basePath
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={`${BASE_PATH}/viajes/${encodeURIComponent(id)}/eventos/${encodeURIComponent(e.idEvento)}/foto`}
                      alt={`Foto del ${(TIPOS_EVENTO[e.tipo] ?? e.tipo).toLowerCase()}`}
                      loading="lazy"
                      className="mt-3 max-h-80 rounded-lg"
                    />
                  )}
                </li>
              ) : null,
            )}
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
  estado?: EstadoViaje;
  subtitulo?: string;
  error?: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 px-4 py-12">
        <div className="flex flex-col gap-1">
          <Link href="/viajes" className="text-sm text-zinc-500 hover:underline">
            ← Viajes
          </Link>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="font-mono text-3xl font-semibold tracking-tight text-black dark:text-zinc-50">{id}</h1>
            {actual && (
              <span className={`rounded-full px-3 py-1 text-sm font-medium ${COLOR_ESTADO_VIAJE[actual] ?? ""}`}>
                {estadoViaje(actual)}
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

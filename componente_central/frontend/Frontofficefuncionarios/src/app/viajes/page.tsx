import { randomUUID } from "node:crypto";
import Form from "next/form";
import Link from "next/link";
import { redirect } from "next/navigation";
import { botonSecundario, Encabezado } from "@/components/encabezado";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend, type RespuestaBackend } from "@/lib/backend";
import { fecha, type ErrorBackend } from "@/lib/casos";
import {
  COLOR_ESTADO_VIAJE,
  ESTADOS_VIAJE,
  estadoViaje,
  nombreChofer,
  type EstadoViaje,
  type PaginaViajes,
} from "@/lib/viajes";

const NO_DISPONIBLE = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";

const FILTROS = Object.keys(ESTADOS_VIAJE) as EstadoViaje[];
const TAMANIO_PAGINA = 20;

type Filtros = { estado: EstadoViaje | null; matricula: string; nroEmpresa: string };

// URL del listado con los filtros y la pagina (la 1 no se escribe)
const urlListado = (f: Filtros, pagina = 1) => {
  const q = new URLSearchParams();
  if (f.estado) q.set("estado", f.estado);
  if (f.matricula) q.set("matricula", f.matricula);
  if (f.nroEmpresa) q.set("nroEmpresa", f.nroEmpresa);
  if (pagina > 1) q.set("pagina", String(pagina));
  const s = q.toString();
  return s ? `/viajes?${s}` : "/viajes";
};

const texto = (v: string | string[] | undefined) => (typeof v === "string" ? v.trim() : "");

// CU Monitorear viajes y pesajes: listado paginado de los viajes asignados, en curso o finalizados de todas las
// empresas. ?estado=EN_CURSO, ?matricula=SBA1234 y ?nroEmpresa=1 filtran, ?pagina=2 elige la pagina (los
// aplica el backend). Que quien consulta sea funcionario lo controla el backend (403 si no).
export default async function Viajes({ searchParams }: PageProps<"/viajes">) {
  const sesion = await getSesion();
  if (!sesion) redirect("/");
  const params = await searchParams;
  const filtros: Filtros = {
    estado: FILTROS.find((e) => e === params.estado) ?? null,
    matricula: texto(params.matricula).slice(0, 20),
    nroEmpresa: texto(params.nroEmpresa).slice(0, 18),
  };
  // Hasta 9 digitos para que siempre entre en el int del backend.
  const pedida = typeof params.pagina === "string" && /^\d{1,9}$/.test(params.pagina) ? Number(params.pagina) : 1;
  const pagina = pedida >= 1 ? pedida : 1;

  const correlationId = randomUUID();
  const q = new URLSearchParams({ pagina: String(pagina), tamanio: String(TAMANIO_PAGINA) });
  if (filtros.estado) q.set("estado", filtros.estado);
  if (filtros.matricula) q.set("matricula", filtros.matricula);
  if (filtros.nroEmpresa) q.set("nroEmpresa", filtros.nroEmpresa);
  const ruta = `/viajes?${q}`;
  let res: RespuestaBackend<PaginaViajes & ErrorBackend>;
  try {
    res = await llamarBackend<PaginaViajes & ErrorBackend>(ruta, { correlationId, usuarioCi: sesion.usuario.cedula });
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return <Pagina sesion={sesion} filtros={filtros} error={NO_DISPONIBLE} />;
  }
  console.info(`[viajes] correlationId=${correlationId} GET ${ruta} status=${res.status}`);

  if (res.status === 401) {
    // Error de configuracion, no del usuario: la X-Api-Key no coincide con la de WildFly
    console.error(`[viajes] correlationId=${correlationId} el backend rechazo la X-Api-Key: revisar BACKEND_API_KEY`);
  }
  if (res.status !== 200 || !Array.isArray(res.data?.viajes)) {
    const error =
      res.status === 403
        ? "No tenés acceso a los viajes: no estás registrado como funcionario del MTOP."
        : res.status === 400 && res.data?.campos
          ? Object.values(res.data.campos).join(" ")
          : "No se pudieron obtener los viajes.";
    return <Pagina sesion={sesion} filtros={filtros} error={error} />;
  }

  const { viajes, total, totalPaginas, tamanio } = res.data;
  // Pagina posterior a la ultima: se ofrece volver a la ultima
  if (viajes.length === 0 && total > 0) {
    return (
      <Pagina sesion={sesion} filtros={filtros}>
        <p className="rounded-lg bg-zinc-100 p-4 text-sm text-zinc-700 dark:bg-zinc-900 dark:text-zinc-300">
          La página {pagina} no existe.{" "}
          <Link href={urlListado(filtros, totalPaginas)} className="font-medium text-[#0068b3] hover:underline dark:text-sky-400">
            Ir a la última página
          </Link>
        </p>
      </Pagina>
    );
  }
  const primero = (pagina - 1) * tamanio + 1;
  const conFiltros = filtros.estado || filtros.matricula || filtros.nroEmpresa;

  return (
    <Pagina sesion={sesion} filtros={filtros}>
      {viajes.length === 0 ? (
        <p className="rounded-lg bg-zinc-100 p-4 text-sm text-zinc-700 dark:bg-zinc-900 dark:text-zinc-300">
          {conFiltros ? "No hay viajes que coincidan con los filtros." : "Todavía no hay viajes."}
        </p>
      ) : (
        <div className="overflow-x-auto rounded-2xl bg-white shadow-sm dark:bg-zinc-950">
          <table className="w-full text-left text-sm">
            <thead className="text-zinc-500">
              <tr>
                <th className="px-4 py-3 font-normal">Guía</th>
                <th className="px-4 py-3 font-normal">Fecha</th>
                <th className="px-4 py-3 font-normal">Recorrido</th>
                <th className="px-4 py-3 font-normal">Empresa</th>
                <th className="px-4 py-3 font-normal">Vehículo</th>
                <th className="px-4 py-3 font-normal">Chofer</th>
                <th className="px-4 py-3 font-normal">Estado</th>
              </tr>
            </thead>
            <tbody>
              {viajes.map((v) => (
                <tr key={v.id} className="border-t border-black/5 hover:bg-zinc-50 dark:border-white/10 dark:hover:bg-zinc-900">
                  <td className="px-4 py-3">
                    <Link
                      href={`/viajes/${encodeURIComponent(v.id)}`}
                      className="font-mono font-medium text-[#0068b3] hover:underline dark:text-sky-400"
                    >
                      {v.id}
                    </Link>
                  </td>
                  <td className="whitespace-nowrap px-4 py-3">{fecha(v.fecha)}</td>
                  <td className="px-4 py-3">
                    {v.origen ?? "-"} → {v.destino ?? "-"}
                  </td>
                  <td className="px-4 py-3">
                    {v.empresa ?? "-"}
                    {v.nroEmpresa != null && <span className="text-zinc-500"> (N° {v.nroEmpresa})</span>}
                  </td>
                  <td className="px-4 py-3 font-mono">{v.matricula ?? "-"}</td>
                  <td className="px-4 py-3">{nombreChofer(v.chofer)}</td>
                  <td className="px-4 py-3">
                    <span className={`whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium ${COLOR_ESTADO_VIAJE[v.estado] ?? ""}`}>
                      {estadoViaje(v.estado)}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {total > 0 && (
        <nav aria-label="Paginación" className="flex flex-wrap items-center justify-between gap-3 text-sm">
          <p className="text-zinc-600 dark:text-zinc-400">
            {primero}–{primero + viajes.length - 1} de {total === 1 ? "1 viaje" : `${total} viajes`}
          </p>
          {totalPaginas > 1 && (
            <div className="flex items-center gap-2">
              <CambioPagina href={pagina > 1 ? urlListado(filtros, pagina - 1) : null}>← Anterior</CambioPagina>
              <span className="px-2 text-zinc-600 dark:text-zinc-400">
                Página {pagina} de {totalPaginas}
              </span>
              <CambioPagina href={pagina < totalPaginas ? urlListado(filtros, pagina + 1) : null}>Siguiente →</CambioPagina>
            </div>
          )}
        </nav>
      )}
    </Pagina>
  );
}

function CambioPagina({ href, children }: { href: string | null; children: React.ReactNode }) {
  const clase = "rounded-full border border-black/10 px-3 py-1 dark:border-white/15";
  return href ? (
    <Link href={href} className={`${clase} transition-colors hover:bg-black/5 dark:hover:bg-white/10`}>
      {children}
    </Link>
  ) : (
    <span aria-disabled className={`${clase} text-zinc-400 dark:text-zinc-600`}>
      {children}
    </span>
  );
}

function Pagina({
  sesion,
  filtros,
  error,
  children,
}: {
  sesion: NonNullable<Awaited<ReturnType<typeof getSesion>>>;
  filtros: Filtros;
  error?: string;
  children?: React.ReactNode;
}) {
  const campo = "h-10 rounded-lg border border-black/15 bg-transparent px-3 text-sm dark:border-white/20";
  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-6 px-4 py-12">
        <h1 className="text-3xl font-semibold tracking-tight text-black dark:text-zinc-50">Viajes</h1>

        <nav aria-label="Filtrar por estado" className="flex flex-wrap gap-2 text-sm">
          {[null, ...FILTROS].map((e) => {
            const activo = e === filtros.estado;
            return (
              <Link
                key={e ?? "todos"}
                href={urlListado({ ...filtros, estado: e })}
                aria-current={activo ? "page" : undefined}
                className={`rounded-full px-3 py-1 transition-colors ${
                  activo
                    ? "bg-[#0068b3] font-medium text-white"
                    : "border border-black/10 text-zinc-700 hover:bg-black/5 dark:border-white/15 dark:text-zinc-300 dark:hover:bg-white/10"
                }`}
              >
                {e ? ESTADOS_VIAJE[e] : "Todos"}
              </Link>
            );
          })}
        </nav>

        {/* GET: los filtros quedan en la URL. next/form agrega el basePath y navega sin recargar. */}
        <Form action="/viajes" className="flex flex-wrap items-end gap-3 text-sm">
          {filtros.estado && <input type="hidden" name="estado" value={filtros.estado} />}
          <label className="flex flex-col gap-1 text-zinc-700 dark:text-zinc-300">
            Matrícula
            <input name="matricula" defaultValue={filtros.matricula} maxLength={20} placeholder="SBA1234" className={campo} />
          </label>
          <label className="flex flex-col gap-1 text-zinc-700 dark:text-zinc-300">
            N° de empresa
            <input
              name="nroEmpresa"
              defaultValue={filtros.nroEmpresa}
              inputMode="numeric"
              pattern="\d*"
              maxLength={18}
              className={`${campo} w-32`}
            />
          </label>
          <button type="submit" className={botonSecundario}>
            Buscar
          </button>
          {(filtros.matricula || filtros.nroEmpresa) && (
            <Link href={urlListado({ ...filtros, matricula: "", nroEmpresa: "" })} className="py-2 text-zinc-500 hover:underline">
              Limpiar búsqueda
            </Link>
          )}
        </Form>

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

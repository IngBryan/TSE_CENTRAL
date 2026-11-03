import { randomUUID } from "node:crypto";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Encabezado } from "@/components/encabezado";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend, type RespuestaBackend } from "@/lib/backend";
import {
  COLOR_ESTADO,
  ESTADOS,
  estado,
  fechaHora,
  tipoCaso,
  type ErrorBackend,
  type EstadoCaso,
  type PaginaCasos,
} from "@/lib/casos";
import { registrarLog } from "@/lib/log";

const NO_DISPONIBLE = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";

const FILTROS = Object.keys(ESTADOS) as EstadoCaso[];
const TAMANIO_PAGINA = 20;

// URL de la bandeja con el filtro y la pagina (la 1 no se escribe)
const urlBandeja = (filtro: EstadoCaso | null, pagina = 1) => {
  const q = new URLSearchParams();
  if (filtro) q.set("estado", filtro);
  if (pagina > 1) q.set("pagina", String(pagina));
  const s = q.toString();
  return s ? `/casos?${s}` : "/casos";
};

// CU Gestionar casos de fiscalizacion: bandeja de trabajo del funcionario, paginada. ?estado=EN_DESCARGO
// filtra por estado y ?pagina=2 elige la pagina (los dos los aplica el backend). Que quien consulta sea
// funcionario lo controla el backend (403 si no).
export default async function Bandeja({ searchParams }: PageProps<"/casos">) {
  const sesion = await getSesion();
  if (!sesion) redirect("/");
  const params = await searchParams;
  const filtro = FILTROS.find((e) => e === params.estado) ?? null;
  // Hasta 9 digitos para que siempre entre en el int del backend.
  const pedida = typeof params.pagina === "string" && /^\d{1,9}$/.test(params.pagina) ? Number(params.pagina) : 1;
  const pagina = pedida >= 1 ? pedida : 1;

  const correlationId = randomUUID();
  const q = new URLSearchParams({ pagina: String(pagina), tamanio: String(TAMANIO_PAGINA) });
  if (filtro) q.set("estado", filtro);
  const ruta = `/casos?${q}`;
  let res: RespuestaBackend<PaginaCasos & ErrorBackend>;
  try {
    res = await llamarBackend<PaginaCasos & ErrorBackend>(ruta, { correlationId, usuarioCi: sesion.usuario.cedula });
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return <Pagina sesion={sesion} filtro={filtro} error={NO_DISPONIBLE} />;
  }
  registrarLog("info", "casos", { correlationId, responseCode: res.status }, { detalle: `GET ${ruta}` });

  if (res.status === 401) {
    // Error de configuracion, no del usuario: la X-Api-Key no coincide con la de WildFly
    registrarLog("error", "casos", { correlationId }, { detalle: `el backend rechazo la X-Api-Key: revisar BACKEND_API_KEY` });
  }
  if (res.status !== 200 || !Array.isArray(res.data?.casos)) {
    const error =
      res.status === 403
        ? "No tenés acceso a los casos de fiscalización: no estás registrado como funcionario del MTOP."
        : "No se pudieron obtener los casos de fiscalización.";
    return <Pagina sesion={sesion} filtro={filtro} error={error} />;
  }

  const { casos, total, totalPaginas, tamanio } = res.data;
  // Pagina posterior a la ultima (p. ej. se resolvieron casos mientras tanto): se ofrece volver a la ultima
  if (casos.length === 0 && total > 0) {
    return (
      <Pagina sesion={sesion} filtro={filtro}>
        <p className="rounded-lg bg-zinc-100 p-4 text-sm text-zinc-700 dark:bg-zinc-900 dark:text-zinc-300">
          La página {pagina} no existe.{" "}
          <Link href={urlBandeja(filtro, totalPaginas)} className="font-medium text-[#0068b3] hover:underline dark:text-sky-400">
            Ir a la última página
          </Link>
        </p>
      </Pagina>
    );
  }
  const primero = (pagina - 1) * tamanio + 1;

  return (
    <Pagina sesion={sesion} filtro={filtro}>
      {casos.length === 0 ? (
        <p className="rounded-lg bg-zinc-100 p-4 text-sm text-zinc-700 dark:bg-zinc-900 dark:text-zinc-300">
          {filtro ? `No hay casos en estado ${estado(filtro).toLowerCase()}.` : "No hay casos de fiscalización."}
        </p>
      ) : (
        <div className="overflow-x-auto rounded-2xl bg-white shadow-sm dark:bg-zinc-950">
          <table className="w-full text-left text-sm">
            <thead className="text-zinc-500">
              <tr>
                <th className="px-4 py-3 font-normal">N°</th>
                <th className="px-4 py-3 font-normal">Tipo</th>
                <th className="px-4 py-3 font-normal">Empresa</th>
                <th className="px-4 py-3 font-normal">Vehículo</th>
                <th className="px-4 py-3 font-normal">Detectado</th>
                <th className="px-4 py-3 font-normal">Estado</th>
              </tr>
            </thead>
            <tbody>
              {casos.map((c) => (
                <tr key={c.id} className="border-t border-black/5 hover:bg-zinc-50 dark:border-white/10 dark:hover:bg-zinc-900">
                  <td className="px-4 py-3">
                    <Link href={`/casos/${c.id}`} className="font-medium text-[#0068b3] hover:underline dark:text-sky-400">
                      {c.id}
                    </Link>
                  </td>
                  <td className="px-4 py-3">
                    <Link href={`/casos/${c.id}`} className="text-black hover:underline dark:text-zinc-50">
                      {tipoCaso(c.tipoCaso)}
                    </Link>
                    {c.descripcion && <p className="line-clamp-1 text-xs text-zinc-500">{c.descripcion}</p>}
                  </td>
                  <td className="px-4 py-3">
                    {c.empresa ?? "-"}
                    {c.nroEmpresa != null && <span className="text-zinc-500"> (N° {c.nroEmpresa})</span>}
                  </td>
                  <td className="px-4 py-3 font-mono">{c.matricula ?? "-"}</td>
                  <td className="whitespace-nowrap px-4 py-3">{fechaHora(c.fechaDeteccion)}</td>
                  <td className="px-4 py-3">
                    <div className="flex flex-col items-start gap-1">
                      {c.estado && (
                        <span className={`whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium ${COLOR_ESTADO[c.estado] ?? ""}`}>
                          {estado(c.estado)}
                        </span>
                      )}
                      {c.puedeResolverse && (
                        <span className="whitespace-nowrap text-xs text-zinc-500">
                          {c.tieneDescargo ? "con descargo · a resolver" : "a resolver"}
                        </span>
                      )}
                    </div>
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
            {primero}–{primero + casos.length - 1} de {total === 1 ? "1 caso" : `${total} casos`}
          </p>
          {totalPaginas > 1 && (
            <div className="flex items-center gap-2">
              <CambioPagina href={pagina > 1 ? urlBandeja(filtro, pagina - 1) : null}>← Anterior</CambioPagina>
              <span className="px-2 text-zinc-600 dark:text-zinc-400">
                Página {pagina} de {totalPaginas}
              </span>
              <CambioPagina href={pagina < totalPaginas ? urlBandeja(filtro, pagina + 1) : null}>Siguiente →</CambioPagina>
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
  filtro,
  error,
  children,
}: {
  sesion: NonNullable<Awaited<ReturnType<typeof getSesion>>>;
  filtro: EstadoCaso | null;
  error?: string;
  children?: React.ReactNode;
}) {
  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col gap-6 px-4 py-12">
        <h1 className="text-3xl font-semibold tracking-tight text-black dark:text-zinc-50">Casos de fiscalización</h1>

        <nav aria-label="Filtrar por estado" className="flex flex-wrap gap-2 text-sm">
          {[null, ...FILTROS].map((e) => {
            const activo = e === filtro;
            return (
              <Link
                key={e ?? "todos"}
                href={urlBandeja(e)}
                aria-current={activo ? "page" : undefined}
                className={`rounded-full px-3 py-1 transition-colors ${
                  activo
                    ? "bg-[#0068b3] font-medium text-white"
                    : "border border-black/10 text-zinc-700 hover:bg-black/5 dark:border-white/15 dark:text-zinc-300 dark:hover:bg-white/10"
                }`}
              >
                {e ? ESTADOS[e] : "Todos"}
              </Link>
            );
          })}
        </nav>

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

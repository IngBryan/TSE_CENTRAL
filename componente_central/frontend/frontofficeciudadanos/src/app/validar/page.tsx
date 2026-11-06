import { randomUUID } from "node:crypto";
import Link from "next/link";
import { Encabezado, botonGubUy } from "@/components/encabezado";
import { Etiquetas } from "@/components/etiquetas";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import { fecha, fechaHora } from "@/lib/infracciones";
import type { EmpresaEncontrada, ValidacionEmpresa, ValidacionVehiculo } from "@/lib/portal";

const NO_DISPONIBLE = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";
const campo =
  "h-10 rounded-lg border border-black/15 bg-transparent px-3 text-base font-normal dark:border-white/20";
const tarjeta = "rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950";

// Validacion de Permisos de Empresas (portal publico, sin login): un cliente busca la transportista por
// numero o por nombre y ve si esta habilitada y, si pone una matricula, si ese vehiculo es de la empresa y
// tiene permiso e ITV vigentes. ?nro=1&matricula=SBA1234 o ?nombre=este
export default async function Validar({ searchParams }: PageProps<"/validar">) {
  const sesion = await getSesion();
  const params = await searchParams;
  const texto = (v: unknown) => (typeof v === "string" ? v.trim() : "");
  const nro = texto(params.nro);
  const matricula = texto(params.matricula);
  const nombre = texto(params.nombre);
  const correlationId = randomUUID();

  let validacion: ValidacionEmpresa | null = null;
  let encontradas: EmpresaEncontrada[] | null = null;
  let error: string | null = null;
  try {
    if (nro) {
      if (!/^\d{1,9}$/.test(nro)) {
        error = "El número de empresa tiene que ser un número.";
      } else {
        const q = matricula ? `?${new URLSearchParams({ matricula })}` : "";
        const res = await llamarBackend<ValidacionEmpresa & { detalle?: string }>(`/publico/empresas/${nro}${q}`, {
          correlationId,
        });
        if (res.status === 200) validacion = res.data;
        else if (res.status === 404) error = "No hay ninguna empresa registrada con ese número.";
        else error = "No se pudo hacer la consulta.";
      }
    } else if (nombre) {
      const res = await llamarBackend<EmpresaEncontrada[] & { campos?: Record<string, string> }>(
        `/publico/empresas?${new URLSearchParams({ nombre })}`,
        { correlationId },
      );
      if (res.status === 200) encontradas = res.data;
      else if (res.status === 400) error = res.data?.campos?.nombre ?? "Revisá el nombre.";
      else error = "No se pudo hacer la consulta.";
    }
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    error = NO_DISPONIBLE;
  }

  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 px-4 py-12">
        <div className="flex flex-col gap-2">
          <Link href="/" className="text-sm text-zinc-500 hover:underline">
            ← Inicio
          </Link>
          <h1 className="text-3xl font-semibold tracking-tight text-black dark:text-zinc-50">Validar una empresa transportista</h1>
          <p className="text-zinc-600 dark:text-zinc-400">
            Antes de contratar un transporte de carga, verificá que la empresa esté habilitada por el MTOP y que el
            vehículo tenga el permiso de circulación y la ITV vigentes. No hace falta ingresar.
          </p>
        </div>

        <form method="get" className={`flex flex-col gap-4 ${tarjeta}`}>
          <div className="flex flex-wrap items-end gap-3">
            <label className="flex flex-col gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300">
              N.º de empresa
              <input name="nro" inputMode="numeric" pattern="\d{1,9}" required defaultValue={nro} className={`${campo} w-36`} />
            </label>
            <label className="flex flex-col gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300">
              Matrícula (opcional)
              <input name="matricula" defaultValue={matricula} maxLength={12} placeholder="SBA1234" className={`${campo} w-40 uppercase`} />
            </label>
            <button type="submit" className={botonGubUy}>
              Validar
            </button>
          </div>
        </form>

        <form method="get" className="flex flex-wrap items-end gap-3 text-sm">
          <label className="flex flex-col gap-1 font-medium text-zinc-700 dark:text-zinc-300">
            ¿No sabés el número? Buscá por nombre
            <input name="nombre" defaultValue={nombre} minLength={3} maxLength={60} required className={`${campo} w-64`} />
          </label>
          <button type="submit" className="flex h-10 items-center rounded-full border border-black/10 px-5 font-medium hover:bg-black/5 dark:border-white/15 dark:hover:bg-white/10">
            Buscar
          </button>
        </form>

        {error && (
          <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
            {error}
          </p>
        )}

        {encontradas &&
          (encontradas.length === 0 ? (
            <p className="text-zinc-600 dark:text-zinc-400">No encontramos empresas con ese nombre.</p>
          ) : (
            <ul className={`flex flex-col divide-y divide-black/5 dark:divide-white/10 ${tarjeta} py-2`}>
              {encontradas.map((e) => (
                <li key={e.nroEmpresa} className="flex flex-wrap items-center justify-between gap-2 py-3">
                  <span>
                    <Link href={`/validar?nro=${e.nroEmpresa}`} className="font-medium underline">
                      {e.nombrePublico}
                    </Link>{" "}
                    <span className="text-sm text-zinc-500">
                      {e.razonSocial} · n.º {e.nroEmpresa}
                    </span>
                  </span>
                  <Estado ok={e.habilitada} si="Habilitada" no="No habilitada" />
                </li>
              ))}
            </ul>
          ))}

        {validacion && <Resultado v={validacion} />}
      </main>
    </div>
  );
}

function Estado({ ok, si, no }: { ok: boolean; si: string; no: string }) {
  return (
    <span
      className={`whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium ${
        ok
          ? "bg-emerald-100 text-emerald-900 dark:bg-emerald-950 dark:text-emerald-200"
          : "bg-red-100 text-red-900 dark:bg-red-950 dark:text-red-200"
      }`}
    >
      {ok ? si : no}
    </span>
  );
}

function Resultado({ v }: { v: ValidacionEmpresa }) {
  return (
    <section className="flex flex-col gap-4" aria-live="polite">
      <div className={`flex flex-col gap-2 ${tarjeta}`}>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-xl font-semibold text-black dark:text-zinc-50">{v.nombrePublico}</h2>
          <Estado ok={v.habilitada} si="Empresa habilitada" no="Empresa no habilitada" />
        </div>
        <p className="text-sm text-zinc-600 dark:text-zinc-400">
          {v.razonSocial} · n.º {v.nroEmpresa}
        </p>
        <p className="text-sm">
          {v.habilitada
            ? `Está habilitada por el MTOP para el transporte de carga.${v.vehiculo ? "" : " Para saber si un vehículo suyo puede circular, ingresá también su matrícula."}`
            : "No está habilitada por el MTOP para el transporte de carga."}
        </p>
        <div className="flex flex-wrap items-center gap-2 text-sm">
          <span className="text-zinc-500">Rubros:</span>
          <Etiquetas valores={v.rubros} vacio="sin rubros registrados" />
        </div>
      </div>
      {v.vehiculo && <Vehiculo v={v.vehiculo} empresa={v.nombrePublico} />}
      <p className="text-xs text-zinc-500">Consulta realizada: {fechaHora(v.consultadoEl)}</p>
    </section>
  );
}

function Vehiculo({ v, empresa }: { v: ValidacionVehiculo; empresa: string }) {
  if (!v.deLaEmpresa) {
    return (
      <div className={`flex flex-col gap-2 ${tarjeta}`}>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h3 className="font-mono text-lg">{v.matricula || "-"}</h3>
          <Estado ok={false} si="" no="No pertenece a la empresa" />
        </div>
        <p className="text-sm text-zinc-600 dark:text-zinc-400">
          Esta matrícula no está registrada como vehículo de {empresa}.
        </p>
      </div>
    );
  }
  return (
    <div className={`flex flex-col gap-3 ${tarjeta}`}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-mono text-lg">{v.matricula}</h3>
        <Estado ok={v.habilitado} si="Vehículo habilitado" no="Vehículo no habilitado" />
      </div>
      <dl className="grid gap-2 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-zinc-500">En actividad</dt>
          <dd>{v.activo ? "Sí" : "No (dado de baja)"}</dd>
        </div>
        <div>
          <dt className="text-zinc-500">Permiso de circulación</dt>
          <dd>{v.permisoVence ? `${v.permisoVigente ? "Vigente" : "Vencido"} (vence ${fecha(v.permisoVence)})` : "Sin permiso"}</dd>
        </div>
        <div>
          <dt className="text-zinc-500">ITV</dt>
          <dd>{v.itvVence ? `${v.itvVigente ? "Vigente" : "Vencida"} (vence ${fecha(v.itvVence)})` : "Sin ITV"}</dd>
        </div>
      </dl>
    </div>
  );
}

import Link from "next/link";
import { redirect } from "next/navigation";
import { Encabezado, botonGubUy, botonSecundario } from "@/components/encabezado";
import type { EstadoPerfil } from "@/lib/auth/perfil";
import { getSesion } from "@/lib/auth/session";
import { BASE_PATH } from "@/lib/base-path";

const ESTADOS: Record<EstadoPerfil, string> = {
  VALIDADO: "Identidad validada",
  PENDIENTE_VALIDACION: "Validación pendiente",
  RECHAZADO: "Rechazado",
  NO_HABILITADO: "No habilitado",
};

// Area privada: sin sesion se vuelve al portal publico.
export default async function MiCuenta({ searchParams }: PageProps<"/mi-cuenta">) {
  const params = await searchParams;
  const sesion = await getSesion();
  if (!sesion) redirect("/");
  const { perfil } = sesion;
  const roles = perfil.roles ?? [];
  const responsable = roles.filter((r) => r.rol === "RESPONSABLE");
  const chofer = roles.filter((r) => r.rol === "CHOFER");

  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-6 px-4 py-12">
        {/* CU-01 FA8: la PDI no respondio, sesion con funcionalidades limitadas */}
        {perfil.estado === "PENDIENTE_VALIDACION" && (
          <p role="status" className="rounded-lg bg-amber-50 p-4 text-amber-900 dark:bg-amber-950 dark:text-amber-200">
            La validación de tus datos está pendiente. Mientras tanto algunas funciones no están disponibles; por
            ejemplo, no podés ser asignado como chofer. Te avisaremos cuando se complete.
          </p>
        )}
        <div className="flex flex-col gap-6 rounded-2xl bg-white p-10 shadow-sm dark:bg-zinc-950">
          <p className="text-lg text-zinc-700 dark:text-zinc-300">
            {params.bienvenida ? "Bienvenido/a, creamos tu perfil" : "Hola"}, {perfil.nombre}.
          </p>
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-zinc-700 dark:text-zinc-300">
            <dt className="font-medium">Cédula</dt>
            <dd>{perfil.cedula}</dd>
            <dt className="font-medium">Email</dt>
            <dd>{perfil.email ?? "—"}</dd>
            <dt className="font-medium">Estado</dt>
            <dd>{ESTADOS[perfil.estado] ?? perfil.estado}</dd>
          </dl>
          <a href={`${BASE_PATH}/auth/logout`} className={botonSecundario}>
            Cerrar sesión
          </a>
        </div>

        {/* CU-01 paso 9: pantalla de inicio segun el rol */}
        <section className="flex flex-col gap-4">
          <h2 className="text-lg font-semibold text-black dark:text-zinc-50">Mis empresas</h2>
          {responsable.map((r) => (
            <div key={`r-${r.nroEmpresa}`} className="flex flex-col gap-3 rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950">
              <p className="text-zinc-700 dark:text-zinc-300">
                Sos responsable de <strong>{r.empresa}</strong> (empresa n.º {r.nroEmpresa}).
              </p>
              <div className="flex flex-wrap gap-3">
                <Link href="/empresa" className={botonSecundario}>
                  Datos de la empresa
                </Link>
                <Link href="/empresa/choferes" className={botonGubUy}>
                  Choferes
                </Link>
                <Link href="/empresa/guias" className={botonGubUy}>
                  Guías de viaje
                </Link>
              </div>
            </div>
          ))}
          {chofer.map((r) => (
            <div key={`c-${r.nroEmpresa}`} className="rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950">
              <p className="text-zinc-700 dark:text-zinc-300">
                Sos chofer de <strong>{r.empresa}</strong>
                {r.estado === "PENDIENTE_VALIDACION"
                  ? " (pendiente de validación: todavía no te pueden asignar viajes)."
                  : "."}
              </p>
            </div>
          ))}
          {roles.length === 0 && (
            <p className="rounded-2xl bg-white p-6 text-zinc-600 shadow-sm dark:bg-zinc-950 dark:text-zinc-400">
              Sin empresa asociada. Cuando una empresa te registre como chofer o el MTOP te asocie como responsable,
              lo vas a ver acá.
            </p>
          )}
        </section>
      </main>
    </div>
  );
}

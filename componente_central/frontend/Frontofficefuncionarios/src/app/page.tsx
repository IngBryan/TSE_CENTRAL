import { redirect } from "next/navigation";
import { Encabezado, botonPrimario } from "@/components/encabezado";
import { loginTestingHabilitado } from "@/lib/auth/config";
import { getSesion } from "@/lib/auth/session";
import { BASE_PATH } from "@/lib/base-path";

const MENSAJES: Record<string, string> = {
  cedula_invalida: "Ingresá una cédula válida.",
};

// Inicio del frontoffice de funcionarios: ingreso de testing con cedula. Con sesion, la pantalla de
// trabajo es la bandeja de casos de fiscalizacion.
export default async function Home({ searchParams }: PageProps<"/">) {
  const sesion = await getSesion();
  if (sesion) redirect("/casos");
  const params = await searchParams;
  const error =
    typeof params.error === "string" ? (MENSAJES[params.error] ?? "No se pudo ingresar. Volvé a intentarlo.") : null;

  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-8 px-4 py-12">
        {error && (
          <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
            {error}
          </p>
        )}
        {params.sesion === "cerrada" && (
          <p className="rounded-lg bg-zinc-100 p-4 text-zinc-700 dark:bg-zinc-900 dark:text-zinc-300">
            Cerraste sesión correctamente.
          </p>
        )}

        <section className="flex flex-col gap-4">
          <h1 className="text-3xl font-semibold tracking-tight text-black sm:text-4xl dark:text-zinc-50">
            Fiscalización del transporte de carga
          </h1>
          <p className="text-lg text-zinc-700 dark:text-zinc-300">
            Revisá los casos de fiscalización detectados, su evidencia y el descargo de la empresa, y resolvelos
            con fundamento.
          </p>
        </section>

        <section className="flex flex-col gap-4">
          {!loginTestingHabilitado() ? (
            <p className="rounded-lg bg-zinc-100 p-4 text-sm text-zinc-700 dark:bg-zinc-900 dark:text-zinc-300">
              El ingreso no está habilitado en este entorno.
            </p>
          ) : (
          <form
            method="post"
            action={`${BASE_PATH}/auth/login`}
            className="flex flex-wrap items-end gap-3 rounded-lg border border-dashed border-amber-500 p-4 text-sm"
          >
            <label className="flex flex-col gap-1 font-medium text-zinc-700 dark:text-zinc-300">
              Ingreso de testing: cédula
              <input
                name="cedula"
                inputMode="numeric"
                required
                className="h-10 rounded-lg border border-black/15 bg-transparent px-3 text-base font-normal dark:border-white/20"
              />
            </label>
            <button type="submit" className={botonPrimario}>
              Ingresar
            </button>
          </form>
          )}
        </section>
      </main>
    </div>
  );
}

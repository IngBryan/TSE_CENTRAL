import { redirect } from "next/navigation";
import { Encabezado, botonPrimario, botonSecundario } from "@/components/encabezado";
import { loginTestingHabilitado } from "@/lib/auth/config";
import { getSesion } from "@/lib/auth/session";
import { BASE_PATH } from "@/lib/base-path";

const MENSAJES: Record<string, string> = {
  cancelado: "No se completó el ingreso. Podés volver a intentarlo cuando quieras.",
  no_es_funcionario: "Tu cédula no está registrada como funcionario del MTOP en carga.uy.",
  no_existe: "No se pudo verificar tu identidad. Comunicate con el administrador.",
  menor_de_edad: "No cumplís con la edad mínima para usar carga.uy.",
  ingreso_no_disponible: "El ingreso no está disponible temporalmente. Probá de nuevo en unos minutos.",
  estado_invalido: "La solicitud de ingreso venció o no es válida. Volvé a intentarlo.",
  token_invalido: "No pudimos validar tu identidad con gub.uy. Volvé a intentarlo.",
  idp_no_disponible: "gub.uy no está disponible en este momento. Probá de nuevo en unos minutos.",
  error_idp: "gub.uy devolvió un error al ingresar. Volvé a intentarlo.",
  cedula_invalida: "Ingresá una cédula de 6 a 8 dígitos, sin puntos ni guion.",
};

// Inicio del frontoffice de funcionarios: ingreso con gub.uy (y, si LOGIN_TESTING=true, con cedula). Con
// sesion, la pantalla de trabajo es la bandeja de casos de fiscalizacion. Tambien recibe la vuelta de ID
// Uruguay: la redirect URI registrada es la raiz de la app.
export default async function Home({ searchParams }: PageProps<"/">) {
  const params = await searchParams;

  if (typeof params.state === "string" && (params.code || params.error)) {
    redirect(`/auth/callback?${new URLSearchParams(params as Record<string, string>)}`);
  }

  const sesion = await getSesion();
  if (sesion) redirect("/casos");
  const error = typeof params.error === "string" ? (MENSAJES[params.error] ?? MENSAJES.error_idp) : null;

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
            con fundamento. Ingresá con tu cuenta de ID Uruguay.
          </p>
          <a href={`${BASE_PATH}/auth/login`} className={`${botonPrimario} h-12 self-start px-6 text-base`}>
            Ingresar con gub.uy
          </a>
        </section>

        {loginTestingHabilitado() && (
          <form
            method="post"
            action={`${BASE_PATH}/auth/testing`}
            className="flex flex-col gap-3 rounded-lg border border-dashed border-amber-500 p-4 text-sm"
          >
            <p className="text-amber-800 dark:text-amber-300">Modo testing</p>
            <div className="flex flex-wrap items-end gap-3">
              <label className="flex flex-col gap-1 font-medium text-zinc-700 dark:text-zinc-300">
                Cédula
                <input
                  name="cedula"
                  inputMode="numeric"
                  pattern="\d{6,8}"
                  required
                  className="h-10 rounded-lg border border-black/15 bg-transparent px-3 text-base font-normal dark:border-white/20"
                />
              </label>
              <button type="submit" className={botonSecundario}>
                Ingresar (testing)
              </button>
            </div>
          </form>
        )}
      </main>
    </div>
  );
}

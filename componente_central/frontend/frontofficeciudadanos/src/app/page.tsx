import { redirect } from "next/navigation";
import { Encabezado, botonGubUy } from "@/components/encabezado";
import { getSesion } from "@/lib/auth/session";

const MENSAJES: Record<string, string> = {
  cancelado: "No se completó el ingreso. Podés volver a intentarlo cuando quieras.",
  no_existe: "No se pudo verificar tu identidad. Comunicate con el MTOP.",
  menor_de_edad: "No cumplís con la edad mínima para usar carga.uy.",
  ingreso_no_disponible: "El ingreso no está disponible temporalmente. Probá de nuevo en unos minutos.",
  estado_invalido: "La solicitud de ingreso venció o no es válida. Volvé a intentarlo.",
  token_invalido: "No pudimos validar tu identidad con gub.uy. Volvé a intentarlo.",
  idp_no_disponible: "gub.uy no está disponible en este momento. Probá de nuevo en unos minutos.",
  error_idp: "gub.uy devolvió un error al ingresar. Volvé a intentarlo.",
};

const SERVICIOS = [
  {
    titulo: "Empresas",
    texto: "Registrá y administrá las empresas de transporte de carga que representás.",
  },
  {
    titulo: "Vehículos",
    texto: "Mantené al día el padrón de camiones y remolques habilitados de tu flota.",
  },
  {
    titulo: "Guías de viaje",
    texto: "Emití y consultá las guías de viaje de tus cargas en todo el país.",
  },
];

// Portal publico. Tambien recibe la vuelta de ID Uruguay: la redirect URI registrada es la raiz.
export default async function Home({ searchParams }: PageProps<"/">) {
  const params = await searchParams;

  if (typeof params.state === "string" && (params.code || params.error)) {
    redirect(`/auth/callback?${new URLSearchParams(params as Record<string, string>)}`);
  }

  const sesion = await getSesion();
  const error = typeof params.error === "string" ? MENSAJES[params.error] ?? MENSAJES.error_idp : null;

  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-12 px-4 py-12">
        {error && (
          <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
            {error}
          </p>
        )}
        {params.sesion === "cerrada" && !sesion && (
          <p className="rounded-lg bg-zinc-100 p-4 text-zinc-700 dark:bg-zinc-900 dark:text-zinc-300">
            Cerraste sesión correctamente.
          </p>
        )}

        <section className="flex max-w-2xl flex-col gap-5">
          <h1 className="text-4xl font-semibold tracking-tight text-black sm:text-5xl dark:text-zinc-50">
            Gestioná el transporte de carga en un solo lugar
          </h1>
          <p className="text-lg text-zinc-700 dark:text-zinc-300">
            carga.uy reúne los trámites de empresas, vehículos y guías de viaje del transporte de carga
            por carretera. Ingresá con tu cuenta de ID Uruguay para empezar.
          </p>
          {!sesion && (
            <a href="/auth/login" className={`${botonGubUy} h-12 self-start px-6 text-base`}>
              Ingresar con gub.uy
            </a>
          )}
        </section>

        <section className="grid gap-4 sm:grid-cols-3">
          {SERVICIOS.map((s) => (
            <article key={s.titulo} className="rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950">
              <h2 className="mb-2 text-lg font-semibold text-black dark:text-zinc-50">{s.titulo}</h2>
              <p className="text-zinc-600 dark:text-zinc-400">{s.texto}</p>
            </article>
          ))}
        </section>
      </main>

      <footer className="border-t border-black/5 py-6 text-center text-sm text-zinc-500 dark:border-white/10">
        carga.uy
      </footer>
    </div>
  );
}

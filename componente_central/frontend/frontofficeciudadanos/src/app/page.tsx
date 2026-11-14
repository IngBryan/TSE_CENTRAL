import Link from "next/link";
import { redirect } from "next/navigation";
import { ArrowRight, BarChart3, Building2, Car, FileText, ShieldCheck } from "lucide-react";
import { Encabezado, botonGubUy, botonSecundario } from "@/components/encabezado";
import { Brillos, Marca } from "@/components/marca";
import { getSesion } from "@/lib/auth/session";
import { BASE_PATH } from "@/lib/base-path";

const MENSAJES: Record<string, string> = {
  cancelado: "No se completó el ingreso. Podés volver a intentarlo cuando quieras.",
  no_existe: "No se pudo verificar tu identidad. Comunicate con el MTOP.",
  menor_de_edad: "No cumplís con la edad mínima para usar carga.uy.",
  ingreso_no_disponible: "El ingreso no está disponible temporalmente. Probá de nuevo en unos minutos.",
  estado_invalido: "La solicitud de ingreso venció o no es válida. Volvé a intentarlo.",
  token_invalido: "No pudimos validar tu identidad con gub.uy. Volvé a intentarlo.",
  idp_no_disponible: "gub.uy no está disponible en este momento. Probá de nuevo en unos minutos.",
  error_idp: "gub.uy devolvió un error al ingresar. Volvé a intentarlo.",
  cedula_invalida: "Ingresá una cédula de 6 a 8 dígitos, sin puntos ni guion.",
};

const SERVICIOS = [
  {
    titulo: "Empresas",
    icono: Building2,
    texto: "Registrá y administrá las empresas de transporte de carga que representás.",
  },
  {
    titulo: "Vehículos",
    icono: Car,
    texto: "Mantené al día el padrón de camiones y remolques habilitados de tu flota.",
  },
  {
    titulo: "Guías de viaje",
    icono: FileText,
    texto: "Emití y consultá las guías de viaje de tus cargas en todo el país.",
  },
];

// Portal publico (sin login): validacion de empresas y estadisticas agregadas
const PUBLICOS = [
  {
    href: "/validar",
    titulo: "Validar una empresa",
    icono: ShieldCheck,
    texto: "Verificá que una transportista esté habilitada y que su vehículo tenga el permiso y la ITV vigentes.",
  },
  {
    href: "/estadisticas",
    titulo: "Estadísticas del transporte de carga",
    icono: BarChart3,
    texto: "Empresas y vehículos habilitados, viajes, carga por rubro y por tipo de carga, e infracciones confirmadas.",
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
    <div className="relative flex flex-1 flex-col overflow-hidden bg-background font-sans">
      <Brillos />
      <Encabezado sesion={sesion} />

      <main className="relative mx-auto flex w-full max-w-6xl flex-1 flex-col gap-14 px-4 py-14">
        {error && (
          <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">
            {error}
          </p>
        )}
        {params.sesion === "cerrada" && !sesion && (
          <p className="rounded-lg border bg-card p-4 text-sm text-muted-foreground shadow-xs">
            Cerraste sesión correctamente.
          </p>
        )}

        <section className="flex max-w-2xl flex-col gap-5">
          <span className="inline-flex items-center gap-2 self-start rounded-full border bg-card/70 px-3 py-1 text-xs font-medium text-muted-foreground shadow-xs backdrop-blur">
            <span className="size-1.5 rounded-full bg-primary" />
            Transporte de carga por carretera
          </span>
          <h1 className="text-4xl font-semibold tracking-tight text-foreground sm:text-5xl">
            Gestioná el transporte de carga{" "}
            <span className="bg-linear-to-r from-[#2a78d6] to-[#6250d6] bg-clip-text text-transparent">
              en un solo lugar
            </span>
          </h1>
          <p className="text-lg text-muted-foreground">
            carga.uy reúne los trámites de empresas, vehículos y guías de viaje del transporte de carga
            por carretera. Ingresá con tu cuenta de ID Uruguay para empezar.
          </p>
          <div className="flex flex-wrap gap-3">
            {sesion ? (
              <Link href="/mi-cuenta" className={`${botonGubUy} h-11 px-5`}>
                Ir a mi cuenta <ArrowRight />
              </Link>
            ) : (
              <a href={`${BASE_PATH}/auth/login`} className={`${botonGubUy} h-11 px-5`}>
                Ingresar con gub.uy <ArrowRight />
              </a>
            )}
            <Link href="/validar" className={`${botonSecundario} h-11 px-5`}>
              <ShieldCheck /> Validar una empresa
            </Link>
          </div>
        </section>

        <section className="grid gap-4 sm:grid-cols-3">
          {SERVICIOS.map(({ titulo, texto, icono: Icono }) => (
            <article key={titulo} className="flex flex-col gap-3 rounded-xl border bg-card p-6 shadow-sm">
              <span className="flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <Icono className="size-5" />
              </span>
              <h2 className="text-lg font-semibold text-foreground">{titulo}</h2>
              <p className="text-sm text-muted-foreground">{texto}</p>
            </article>
          ))}
        </section>

        <section className="flex flex-col gap-4">
          <div>
            <h2 className="text-2xl font-semibold tracking-tight text-foreground">Sin ingresar</h2>
            <p className="text-sm text-muted-foreground">Información pública, disponible para cualquier persona.</p>
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            {PUBLICOS.map(({ href, titulo, texto, icono: Icono }) => (
              <Link
                key={href}
                href={href}
                className="group flex gap-4 rounded-xl border bg-card p-6 shadow-sm transition-all hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-md"
              >
                <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                  <Icono className="size-5" />
                </span>
                <span className="flex flex-col gap-1">
                  <span className="flex items-center gap-1.5 text-lg font-semibold text-foreground group-hover:text-primary">
                    {titulo}
                    <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" />
                  </span>
                  <span className="text-sm text-muted-foreground">{texto}</span>
                </span>
              </Link>
            ))}
          </div>
        </section>
      </main>

      <footer className="relative border-t py-6 text-sm text-muted-foreground">
        <div className="mx-auto flex w-full max-w-6xl flex-wrap items-center justify-between gap-4 px-4">
          <span className="flex items-center gap-2 font-medium text-foreground">
            <Marca className="size-6 [&_svg]:size-3" /> CARGA.UY
          </span>
          <span>Ministerio de Transporte y Obras Públicas</span>
        </div>
      </footer>
    </div>
  );
}

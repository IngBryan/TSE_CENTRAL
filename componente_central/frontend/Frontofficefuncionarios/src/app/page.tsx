import { redirect } from "next/navigation";
import { Building2, ClipboardList, Route, ShieldCheck } from "lucide-react";
import { botonPrimario } from "@/components/encabezado";
import { Brillos, Marca } from "@/components/marca";
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

// Inicio del frontoffice de funcionarios: ingreso con gub.uy (el de testing va por URL: /auth/testing?cedula=...). Con
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
    // Tarjeta de ingreso con los brillos azul y violeta detras, como el login del backoffice
    <main className="relative flex flex-1 items-center justify-center overflow-hidden bg-background px-4 py-16 font-sans">
      <Brillos />

      <section className="relative flex w-full max-w-md flex-col gap-6 rounded-2xl border bg-card p-8 shadow-xl shadow-primary/5">
        <div className="flex items-center gap-3">
          <Marca className="size-11 rounded-xl shadow-md [&_svg]:size-5" />
          <div className="leading-tight">
            <p className="font-semibold tracking-tight">CARGA.UY</p>
            <p className="text-sm text-muted-foreground">Funcionarios MTOP</p>
          </div>
        </div>

        <div className="flex flex-col gap-1.5">
          <h1 className="text-2xl font-semibold tracking-tight">Fiscalización del transporte de carga</h1>
          <p className="text-sm text-muted-foreground">
            Revisá los casos detectados, su evidencia y el descargo de la empresa, y resolvelos con fundamento.
          </p>
        </div>

        {error && (
          <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
            {error}
          </p>
        )}
        {params.sesion === "cerrada" && (
          <p className="rounded-lg border bg-muted/50 p-3 text-sm text-muted-foreground">Cerraste sesión correctamente.</p>
        )}

        <a href={`${BASE_PATH}/auth/login`} className={`${botonPrimario} h-11 w-full`}>
          <ShieldCheck /> Ingresar con ID Uruguay
        </a>

        <ul className="grid gap-2 border-t pt-5 text-sm text-muted-foreground">
          {[
            { icono: ClipboardList, texto: "Bandeja de casos de fiscalización" },
            { icono: Route, texto: "Seguimiento de viajes en tiempo real" },
            { icono: Building2, texto: "Gestión de empresas y vehículos" },
          ].map(({ icono: Icono, texto }) => (
            <li key={texto} className="flex items-center gap-2.5">
              <span className="flex size-7 items-center justify-center rounded-md bg-primary/10 text-primary">
                <Icono className="size-3.5" />
              </span>
              {texto}
            </li>
          ))}
        </ul>
      </section>
    </main>
  );
}

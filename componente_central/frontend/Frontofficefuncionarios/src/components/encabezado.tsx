import { Building2, ClipboardList, LogOut, Route } from "lucide-react";
import Link from "next/link";
import { BASE_PATH } from "@/lib/base-path";
import type { Sesion } from "@/lib/auth/session";
import { Marca } from "./marca";
import { NavLink } from "./nav-link";

// Mismo aspecto que el Button del backoffice: esquinas rounded-lg, azul primario con sombra suave y
// secundario con borde.
export const linkBoton =
  "flex h-10 items-center justify-center gap-2 rounded-lg px-4 text-sm font-medium whitespace-nowrap transition-colors disabled:pointer-events-none disabled:opacity-50 [&_svg]:size-4";
export const botonPrimario = `${linkBoton} bg-primary text-primary-foreground shadow-sm shadow-primary/30 hover:bg-primary/90`;
export const botonSecundario = `${linkBoton} border bg-card text-foreground shadow-xs hover:bg-accent hover:text-accent-foreground`;

const SECCIONES = [
  { href: "/casos", nombre: "Casos", icono: ClipboardList },
  { href: "/viajes", nombre: "Viajes", icono: Route },
  { href: "/empresas", nombre: "Empresas", icono: Building2 },
];

const iniciales = (nombre: string) =>
  nombre
    .split(/\s+/)
    .filter((p) => p && !p.startsWith("C.I."))
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join("") || "F";

// Barra superior del frontoffice de funcionarios, con la marca y el estilo del backoffice.
export function Encabezado({ sesion }: { sesion: Sesion | null }) {
  return (
    <header className="sticky top-0 z-30 border-b bg-background/80 backdrop-blur print:hidden">
      <div className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between gap-4 px-4">
        <Link href="/" className="flex items-center gap-2.5">
          <Marca />
          <span className="grid leading-tight">
            <span className="font-semibold tracking-tight text-foreground">CARGA.UY</span>
            <span className="text-xs text-muted-foreground">Funcionarios</span>
          </span>
        </Link>

        {sesion && (
          <>
            <nav className="flex flex-1 items-center justify-center gap-1">
              {SECCIONES.map(({ href, nombre, icono: Icono }) => (
                <NavLink key={href} href={href}>
                  <Icono />
                  <span className="hidden sm:inline">{nombre}</span>
                </NavLink>
              ))}
            </nav>

            <div className="flex items-center gap-3">
              <div className="hidden items-center gap-2 md:flex" title={sesion.usuario.nombre}>
                <span className="flex size-8 items-center justify-center rounded-full bg-accent text-xs font-semibold text-accent-foreground">
                  {iniciales(sesion.usuario.nombre)}
                </span>
                <span className="grid max-w-44 text-xs leading-tight">
                  <span className="truncate font-medium text-foreground">{sesion.usuario.nombre}</span>
                  <span className="text-muted-foreground">Funcionario MTOP</span>
                </span>
              </div>
              <form method="post" action={`${BASE_PATH}/auth/logout`}>
                <button type="submit" className={botonSecundario} title="Cerrar sesión">
                  <LogOut />
                  <span className="hidden lg:inline">Cerrar sesión</span>
                </button>
              </form>
            </div>
          </>
        )}
      </div>
      {sesion?.usuario.identidadVerificada === false && (
        <p
          role="status"
          className="border-t border-amber-200 bg-amber-50 px-4 py-2 text-center text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200"
        >
          Identidad no verificada con DNIC: el servicio no estaba disponible al ingresar. Se vuelve a verificar
          en el próximo ingreso.
        </p>
      )}
    </header>
  );
}

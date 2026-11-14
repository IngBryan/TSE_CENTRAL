import { TriangleAlert, Car, FileText, IdCard, UserRound } from "lucide-react";
import Link from "next/link";
import { obtenerNotificaciones } from "@/app/empresa/infracciones/notificaciones";
import type { Sesion } from "@/lib/auth/session";
import { BASE_PATH } from "@/lib/base-path";
import { Campana } from "./campana";
import { botonGubUy, botonSecundario } from "./estilos";
import { Marca } from "./marca";
import { NavLink } from "./nav-link";

export { botonGubUy, botonSecundario, linkBoton } from "./estilos";

// Accesos del responsable de una empresa (los mismos de Mi cuenta)
const SECCIONES_RESPONSABLE = [
  { href: "/empresa/vehiculos", nombre: "Vehículos", icono: Car },
  { href: "/empresa/choferes", nombre: "Choferes", icono: IdCard },
  { href: "/empresa/guias", nombre: "Guías", icono: FileText },
  { href: "/empresa/infracciones", nombre: "Infracciones", icono: TriangleAlert },
];

// Barra superior comun al portal publico y al area privada, con la marca del backoffice. Al responsable
// de una empresa le muestra sus secciones y la campana con las notificaciones de casos de fiscalizacion.
// Es de servidor: los componentes de cliente importan los estilos de ./estilos.
export async function Encabezado({ sesion }: { sesion: Sesion | null }) {
  const esResponsable = sesion?.perfil.roles?.some((r) => r.rol === "RESPONSABLE") ?? false;
  const notificaciones = esResponsable ? await obtenerNotificaciones() : null;

  return (
    <header className="sticky top-0 z-30 border-b bg-background/80 backdrop-blur print:hidden">
      <div className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between gap-4 px-4">
        <Link href="/" className="flex items-center gap-2.5">
          <Marca />
          <span className="grid leading-tight">
            <span className="font-semibold tracking-tight text-foreground">CARGA.UY</span>
            <span className="text-xs text-muted-foreground">Ciudadanos</span>
          </span>
        </Link>

        {esResponsable && (
          <nav className="hidden flex-1 items-center justify-center gap-1 lg:flex">
            {SECCIONES_RESPONSABLE.map(({ href, nombre, icono: Icono }) => (
              <NavLink key={href} href={href}>
                <Icono />
                {nombre}
              </NavLink>
            ))}
          </nav>
        )}

        {sesion ? (
          <div className="flex items-center gap-2">
            {notificaciones && <Campana inicial={notificaciones} />}
            <Link href="/mi-cuenta" className={botonSecundario} title={sesion.perfil.nombre}>
              <UserRound />
              <span className="hidden max-w-40 truncate sm:inline">{sesion.perfil.nombre}</span>
              <span className="sm:hidden">Mi cuenta</span>
            </Link>
          </div>
        ) : (
          <a href={`${BASE_PATH}/auth/login`} className={botonGubUy}>
            Ingresar con gub.uy
          </a>
        )}
      </div>
    </header>
  );
}

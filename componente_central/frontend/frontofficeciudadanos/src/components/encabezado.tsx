import Link from "next/link";
import { obtenerNotificaciones } from "@/app/empresa/infracciones/notificaciones";
import type { Sesion } from "@/lib/auth/session";
import { BASE_PATH } from "@/lib/base-path";
import { Campana } from "./campana";
import { botonGubUy, botonSecundario } from "./estilos";

export { botonGubUy, botonSecundario, linkBoton } from "./estilos";

// Barra superior comun al portal publico y al area privada. Al responsable de una empresa le muestra la
// campana con las notificaciones de casos de fiscalizacion. Es de servidor: los componentes de cliente
// importan los estilos de ./estilos.
export async function Encabezado({ sesion }: { sesion: Sesion | null }) {
  const notificaciones = sesion?.perfil.roles?.some((r) => r.rol === "RESPONSABLE")
    ? await obtenerNotificaciones()
    : null;

  return (
    <header className="border-b border-black/5 bg-white dark:border-white/10 dark:bg-zinc-950">
      <div className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between gap-4 px-4">
        <Link href="/" className="text-lg font-semibold tracking-tight text-black dark:text-zinc-50">
          carga.uy <span className="font-normal text-zinc-500">· Ciudadanos</span>
        </Link>
        {sesion ? (
          <nav className="flex items-center gap-3">
            <span className="hidden text-sm text-zinc-600 sm:inline dark:text-zinc-400">{sesion.perfil.nombre}</span>
            {notificaciones && <Campana inicial={notificaciones} />}
            <Link href="/mi-cuenta" className={botonSecundario}>
              Mi cuenta
            </Link>
          </nav>
        ) : (
          <a href={`${BASE_PATH}/auth/login`} className={botonGubUy}>
            Ingresar con gub.uy
          </a>
        )}
      </div>
    </header>
  );
}

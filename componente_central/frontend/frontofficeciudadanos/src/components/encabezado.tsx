import Link from "next/link";
import type { Sesion } from "@/lib/auth/session";
import { BASE_PATH } from "@/lib/base-path";

export const linkBoton =
  "flex h-10 items-center justify-center rounded-full px-5 text-sm font-medium transition-colors";
export const botonGubUy = `${linkBoton} bg-[#0068b3] text-white hover:bg-[#00558f]`;
export const botonSecundario = `${linkBoton} border border-black/10 hover:bg-black/5 dark:border-white/15 dark:hover:bg-white/10`;

// Barra superior comun al portal publico y al area privada.
export function Encabezado({ sesion }: { sesion: Sesion | null }) {
  return (
    <header className="border-b border-black/5 bg-white dark:border-white/10 dark:bg-zinc-950">
      <div className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between gap-4 px-4">
        <Link href="/" className="text-lg font-semibold tracking-tight text-black dark:text-zinc-50">
          carga.uy <span className="font-normal text-zinc-500">· Ciudadanos</span>
        </Link>
        {sesion ? (
          <nav className="flex items-center gap-3">
            <span className="hidden text-sm text-zinc-600 sm:inline dark:text-zinc-400">{sesion.perfil.nombre}</span>
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

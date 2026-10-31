import Link from "next/link";
import { BASE_PATH } from "@/lib/base-path";
import type { Sesion } from "@/lib/auth/session";

export const linkBoton =
  "flex h-10 items-center justify-center rounded-full px-5 text-sm font-medium transition-colors disabled:opacity-50";
export const botonPrimario = `${linkBoton} bg-[#0068b3] text-white hover:bg-[#00558f]`;
export const botonSecundario = `${linkBoton} border border-black/10 hover:bg-black/5 dark:border-white/15 dark:hover:bg-white/10`;

// Barra superior del frontoffice de funcionarios (mismo estilo que el de ciudadanos).
export function Encabezado({ sesion }: { sesion: Sesion | null }) {
  return (
    <header className="border-b border-black/5 bg-white dark:border-white/10 dark:bg-zinc-950">
      <div className="mx-auto flex h-16 w-full max-w-6xl items-center justify-between gap-4 px-4">
        <Link href="/" className="text-lg font-semibold tracking-tight text-black dark:text-zinc-50">
          carga.uy <span className="font-normal text-zinc-500">· Funcionarios</span>
        </Link>
        {sesion && (
          <nav className="flex items-center gap-3">
            <Link href="/casos" className="text-sm text-zinc-700 hover:underline dark:text-zinc-300">
              Casos
            </Link>
            <Link href="/viajes" className="text-sm text-zinc-700 hover:underline dark:text-zinc-300">
              Viajes
            </Link>
            <Link href="/empresas" className="text-sm text-zinc-700 hover:underline dark:text-zinc-300">
              Empresas
            </Link>
            <span className="hidden text-sm text-zinc-600 sm:inline dark:text-zinc-400">{sesion.usuario.nombre}</span>
            <form method="post" action={`${BASE_PATH}/auth/logout`}>
              <button type="submit" className={botonSecundario}>
                Cerrar sesión
              </button>
            </form>
          </nav>
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

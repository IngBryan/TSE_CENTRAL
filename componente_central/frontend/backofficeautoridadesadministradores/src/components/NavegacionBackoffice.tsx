
import Link from "next/link";

const SECCIONES = [
  { ruta: "/nodos-perifericos", nombre: "Nodos periféricos" },
  { ruta: "/usuarios", nombre: "Usuarios" },
] as const;

type Props = {
  actual: (typeof SECCIONES)[number]["ruta"];
};

/**
 * Navegación entre las secciones del administrador.
 */
export default function NavegacionBackoffice({ actual }: Props) {
  return (
    <nav aria-label="Secciones" className="flex gap-1 border-b">
      {SECCIONES.map((seccion) => (
        <Link
          key={seccion.ruta}
          href={seccion.ruta}
          aria-current={seccion.ruta === actual ? "page" : undefined}
          className={
            seccion.ruta === actual
              ? "-mb-px border-b-2 border-primary px-4 py-2 text-sm font-medium"
              : "px-4 py-2 text-sm text-muted-foreground hover:text-foreground"
          }
        >
          {seccion.nombre}
        </Link>
      ))}
    </nav>
  );
}

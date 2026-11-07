
import Link from "next/link";
import { redirect } from "next/navigation";
import type { ReactNode } from "react";

import { cerrarSesionAction } from "@/app/login/actions";
import NavegacionBackoffice from "@/components/NavegacionBackoffice";
import { consultarIdentidad } from "@/lib/backoffice-sesion";
import { listarUsuarios } from "@/lib/usuarios-backend";
import {
  NOMBRE_ROL,
  esRolUsuario,
  type DatosUsuario,
  type FiltrosUsuarios as Filtros,
} from "@/lib/usuarios";
import FiltrosUsuarios from "./FiltrosUsuarios";
import PanelAltaUsuario from "./PanelAltaUsuario";

const TAMANIO_PAGINA = 20;

type Parametro = string | string[] | undefined;

function primero(valor: Parametro): string | undefined {
  return Array.isArray(valor) ? valor[0] : valor;
}

// Un rol desconocido se ignora; la cédula la valida el backend
function leerFiltros(parametros: Record<string, Parametro>): Filtros {
  const rol = primero(parametros.rol);

  return {
    rol: esRolUsuario(rol) ? rol : null,
    ci: (primero(parametros.ci) ?? "").trim().slice(0, 20),
  };
}

// La URL de una página conserva los filtros aplicados
function urlPagina(pagina: number, filtros: Filtros): string {
  const parametros = new URLSearchParams({ pagina: String(pagina) });

  if (filtros.rol) parametros.set("rol", filtros.rol);
  if (filtros.ci) parametros.set("ci", filtros.ci);

  return `/usuarios?${parametros}`;
}

// Un valor que no sea un entero positivo muestra la primera página
function numeroDePagina(valor: Parametro): number {
  const texto = primero(valor);

  if (!texto || !/^\d+$/.test(texto)) return 1;

  const numero = Number(texto);

  return Number.isSafeInteger(numero) && numero >= 1 ? numero : 1;
}

function nombreCompleto(usuario: DatosUsuario): string {
  const nombre = [usuario.nombres, usuario.apellidos]
    .filter(Boolean)
    .join(" ");

  return nombre || "—";
}

function EnlacePagina({
  href,
  habilitado,
  children,
}: {
  href: string;
  habilitado: boolean;
  children: ReactNode;
}) {
  const clases = "rounded-md border px-3 py-1.5 text-sm";

  if (!habilitado) {
    return (
      <span aria-disabled="true" className={`${clases} opacity-50`}>
        {children}
      </span>
    );
  }

  return (
    <Link href={href} className={`${clases} hover:bg-muted`}>
      {children}
    </Link>
  );
}

export default async function UsuariosPage({
  searchParams,
}: PageProps<"/usuarios">) {
  const identidad = await consultarIdentidad();

  if (identidad?.rol !== "ADMINISTRADOR") {
    redirect("/login");
  }

  const parametros = await searchParams;
  const pagina = numeroDePagina(parametros.pagina);
  const filtros = leerFiltros(parametros);
  const hayFiltros = filtros.rol !== null || filtros.ci !== "";
  const resultado = await listarUsuarios(pagina, TAMANIO_PAGINA, filtros);

  return (
    <main className="mx-auto max-w-6xl space-y-6 px-6 py-10">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold">Usuarios del sistema</h1>

          <p className="mt-2 text-muted-foreground">
            Funcionarios, responsables de empresa, choferes,
            administradores y autoridades.
          </p>

          <p className="mt-1 text-xs text-muted-foreground">
            Sesión: {identidad.ci} · Administrador
          </p>
        </div>

        <form action={cerrarSesionAction}>
          <button
            type="submit"
            className="rounded-md border px-4 py-2 text-sm hover:bg-muted"
          >
            Cerrar sesión
          </button>
        </form>
      </header>

      <NavegacionBackoffice actual="/usuarios" />

      <PanelAltaUsuario />

      <FiltrosUsuarios filtros={filtros} />

      {!resultado.ok ? (
        <div
          role="alert"
          className="rounded-lg border p-4 text-sm text-destructive"
        >
          {resultado.mensaje}
        </div>
      ) : resultado.pagina.usuarios.length === 0 ? (
        <div className="space-y-4">
          <div className="rounded-lg border p-8 text-center text-muted-foreground">
            {resultado.pagina.total > 0
              ? "Esta página no tiene usuarios."
              : hayFiltros
                ? "Ningún usuario coincide con la búsqueda."
                : "No hay usuarios registrados."}
          </div>

          {resultado.pagina.total > 0 && (
            <EnlacePagina href={urlPagina(1, filtros)} habilitado>
              Ir a la primera página
            </EnlacePagina>
          )}
        </div>
      ) : (
        <section className="space-y-4">
          <div className="overflow-x-auto rounded-lg border">
            <table className="w-full text-left text-sm">
              <thead className="bg-muted">
                <tr>
                  <th className="p-3">Cédula</th>
                  <th className="p-3">Nombre</th>
                  <th className="p-3">Email</th>
                  <th className="p-3">Roles</th>
                </tr>
              </thead>

              <tbody>
                {resultado.pagina.usuarios.map((usuario) => (
                  <tr key={usuario.ci} className="border-t">
                    <td className="p-3 font-mono">{usuario.ci}</td>
                    <td className="p-3">{nombreCompleto(usuario)}</td>
                    <td className="p-3">{usuario.email ?? "—"}</td>
                    <td className="p-3">
                      <ul className="flex flex-wrap gap-1.5">
                        {usuario.roles.map((rol) => (
                          <li
                            key={rol}
                            className="rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium"
                          >
                            {NOMBRE_ROL[rol] ?? rol}
                          </li>
                        ))}
                      </ul>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <nav
            aria-label="Paginación"
            className="flex flex-wrap items-center justify-between gap-3"
          >
            <p className="text-sm text-muted-foreground">
              Página {resultado.pagina.pagina} de{" "}
              {resultado.pagina.totalPaginas} ·{" "}
              {resultado.pagina.total}{" "}
              {resultado.pagina.total === 1 ? "usuario" : "usuarios"}
            </p>

            <div className="flex gap-2">
              <EnlacePagina
                href={urlPagina(resultado.pagina.pagina - 1, filtros)}
                habilitado={resultado.pagina.pagina > 1}
              >
                Anterior
              </EnlacePagina>

              <EnlacePagina
                href={urlPagina(resultado.pagina.pagina + 1, filtros)}
                habilitado={
                  resultado.pagina.pagina < resultado.pagina.totalPaginas
                }
              >
                Siguiente
              </EnlacePagina>
            </div>
          </nav>
        </section>
      )}
    </main>
  );
}

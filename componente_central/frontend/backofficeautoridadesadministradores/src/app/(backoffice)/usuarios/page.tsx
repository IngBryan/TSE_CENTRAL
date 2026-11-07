import Link from "next/link";
import type { ReactNode } from "react";

import { Button } from "@/components/ui/button";
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";
import { Table, TableBody, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { requerirRol } from "@/lib/backoffice-sesion";
import { listarUsuarios } from "@/lib/usuarios-backend";
import {
  esRolUsuario,
  type DatosUsuario,
  type FiltrosUsuarios as Filtros,
} from "@/lib/usuarios";
import FiltrosUsuarios from "./FiltrosUsuarios";
import FilaUsuario from "./FilaUsuario";
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

// El backend lee la página como int de Java: un número mayor le da error
const PAGINA_MAXIMA = 2_147_483_647;

// Un valor que no sea un entero positivo que el backend acepte muestra la
// primera página
function numeroDePagina(valor: Parametro): number {
  const texto = primero(valor);

  if (!texto || !/^\d+$/.test(texto)) return 1;

  const numero = Number(texto);

  return numero >= 1 && numero <= PAGINA_MAXIMA ? numero : 1;
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
  if (!habilitado) {
    return (
      <Button variant="outline" size="sm" disabled>
        {children}
      </Button>
    );
  }

  return (
    <Button variant="outline" size="sm" nativeButton={false} render={<Link href={href} />}>
      {children}
    </Button>
  );
}

export default async function UsuariosPage({
  searchParams,
}: PageProps<"/usuarios">) {
  const identidad = await requerirRol("ADMINISTRADOR");

  const parametros = await searchParams;
  const pagina = numeroDePagina(parametros.pagina);
  const filtros = leerFiltros(parametros);
  const hayFiltros = filtros.rol !== null || filtros.ci !== "";
  const resultado = await listarUsuarios(pagina, TAMANIO_PAGINA, filtros);

  return (
    <div className="w-full min-w-0 space-y-6 px-6 py-8 lg:px-8">
      <header>
        <h1 className="text-3xl font-semibold tracking-tight">Usuarios del sistema</h1>

        <p className="mt-2 text-muted-foreground">
          Funcionarios, responsables de empresa, choferes,
          administradores y autoridades.
        </p>
      </header>

      <PanelAltaUsuario />

      <FiltrosUsuarios filtros={filtros} />

      {!resultado.ok ? (
        <div
          role="alert"
          className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive"
        >
          {resultado.mensaje}
        </div>
      ) : resultado.pagina.usuarios.length === 0 ? (
        <div className="space-y-4">
          <div className="rounded-xl border border-dashed bg-card p-8 text-center text-muted-foreground">
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
          <div className="overflow-hidden rounded-xl border bg-card shadow-sm">
            <Table>
              <TableHeader className="bg-muted/70">
                <TableRow className="hover:bg-transparent">
                  {["Cédula", "Nombre", "Email", "Roles"].map((titulo) => (
                    <TableHead
                      key={titulo}
                      className="px-3 text-xs font-semibold tracking-wide text-muted-foreground uppercase"
                    >
                      {titulo}
                    </TableHead>
                  ))}
                  <TableHead className="px-3">
                    <span className="sr-only">Acciones</span>
                  </TableHead>
                </TableRow>
              </TableHeader>

              <TableBody>
                {resultado.pagina.usuarios.map((usuario) => (
                  <FilaUsuario
                    key={usuario.ci}
                    usuario={usuario}
                    nombre={nombreCompleto(usuario)}
                    esPropio={usuario.ci === identidad.ci}
                  />
                ))}
              </TableBody>
            </Table>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-muted-foreground">
              Página {resultado.pagina.pagina} de{" "}
              {resultado.pagina.totalPaginas} ·{" "}
              {resultado.pagina.total}{" "}
              {resultado.pagina.total === 1 ? "usuario" : "usuarios"}
            </p>

            <Pagination className="mx-0 w-auto">
              <PaginationContent>
                <PaginationItem>
                  <PaginationPrevious
                    text="Anterior"
                    href={urlPagina(Math.max(resultado.pagina.pagina - 1, 1), filtros)}
                    aria-disabled={resultado.pagina.pagina <= 1}
                    className={resultado.pagina.pagina <= 1 ? "pointer-events-none opacity-40" : ""}
                  />
                </PaginationItem>
                <PaginationItem>
                  <PaginationNext
                    text="Siguiente"
                    href={urlPagina(resultado.pagina.pagina + 1, filtros)}
                    aria-disabled={resultado.pagina.pagina >= resultado.pagina.totalPaginas}
                    className={
                      resultado.pagina.pagina >= resultado.pagina.totalPaginas
                        ? "pointer-events-none opacity-40"
                        : ""
                    }
                  />
                </PaginationItem>
              </PaginationContent>
            </Pagination>
          </div>
        </section>
      )}
    </div>
  );
}

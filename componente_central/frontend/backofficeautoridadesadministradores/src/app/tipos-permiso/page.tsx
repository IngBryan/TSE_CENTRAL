
import Link from "next/link";
import { redirect } from "next/navigation";

import { cerrarSesionAction } from "@/app/login/actions";
import { consultarIdentidad } from "@/lib/backoffice-sesion";
import { listarTiposPermiso } from "@/lib/tipos-permiso-backend";
import FilaTipoPermiso from "./FilaTipoPermiso";
import FormularioTipoPermiso from "./FormularioTipoPermiso";

export default async function TiposPermisoPage() {
  const identidad = await consultarIdentidad();

  if (identidad?.rol !== "ADMINISTRADOR") {
    redirect("/login");
  }

  const resultado = await listarTiposPermiso();

  return (
    <main className="mx-auto max-w-6xl space-y-6 px-6 py-10">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <Link
            href="/nodos-perifericos"
            className="text-sm text-muted-foreground hover:underline"
          >
            ← Nodos periféricos
          </Link>

          <h1 className="text-3xl font-semibold">Tipos de permiso</h1>

          <p className="mt-2 text-muted-foreground">
            Permisos a demanda, además del Permiso Nacional de Circulación y
            la ITV. Un funcionario los asigna a cada empresa.
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

      <section className="rounded-lg border p-6">
        <h2 className="mb-3 text-lg font-semibold">Nuevo tipo de permiso</h2>
        <FormularioTipoPermiso />
      </section>

      {!resultado.ok ? (
        <div
          role="alert"
          className="rounded-lg border p-4 text-sm text-destructive"
        >
          {resultado.mensaje}
        </div>
      ) : resultado.tipos.length === 0 ? (
        <div className="rounded-lg border p-8 text-center text-muted-foreground">
          No hay tipos de permiso registrados.
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border">
          <table className="w-full text-left text-sm">
            <thead className="bg-muted">
              <tr>
                <th className="p-3">Nombre</th>
                <th className="p-3">Pide número</th>
                <th className="p-3">Pide vencimiento</th>
                <th className="p-3">Estado</th>
                <th className="p-3" />
              </tr>
            </thead>

            <tbody>
              {resultado.tipos.map((tipo) => (
                <FilaTipoPermiso key={tipo.id} tipo={tipo} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  );
}

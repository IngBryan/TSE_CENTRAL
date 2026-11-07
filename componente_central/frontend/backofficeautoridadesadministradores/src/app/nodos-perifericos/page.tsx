
import { listarNodos } from "@/lib/nodos-perifericos-backend";
import PanelAltaNodo from "./PanelAltaNodo";
import { redirect } from "next/navigation";
import { consultarIdentidad } from "@/lib/backoffice-sesion";
import { cerrarSesionAction } from "@/app/login/actions";
import NavegacionBackoffice from "@/components/NavegacionBackoffice";

function formatearFecha(valor: string | null): string {
  if (!valor) return "Sin comunicaciones";

  const fecha = new Date(valor);

  if (Number.isNaN(fecha.getTime())) {
    return "Fecha inválida";
  }

  return fecha.toLocaleString("es-UY", {
    timeZone: "America/Montevideo",
    dateStyle: "short",
    timeStyle: "short",
  });
}

export default async function GestionNodosPerifericosPage() {
  const identidad = await consultarIdentidad();

  if (identidad?.rol !== "ADMINISTRADOR") {
    redirect("/login");
  }

  const resultado = await listarNodos();

  return (
    <main className="mx-auto max-w-6xl space-y-6 px-6 py-10">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold">
            Gestión de nodos periféricos
          </h1>

          <p className="mt-2 text-muted-foreground">
            Administración de balanzas y dispositivos de tracking.
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

      <NavegacionBackoffice actual="/nodos-perifericos" />

      <PanelAltaNodo />

      {!resultado.ok ? (
        <div
          role="alert"
          className="rounded-lg border p-4 text-sm text-destructive"
        >
          {resultado.mensaje}
        </div>
      ) : resultado.nodos.length === 0 ? (
        <div className="rounded-lg border p-8 text-center text-muted-foreground">
          No hay nodos periféricos registrados.
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border">
          <table className="w-full text-left text-sm">
            <thead className="bg-muted">
              <tr>
                <th className="p-3">Tipo</th>
                <th className="p-3">Nombre</th>
                <th className="p-3">Ubicación</th>
                <th className="p-3">ID externo</th>
                <th className="p-3">Estado</th>
                <th className="p-3">Última comunicación</th>
              </tr>
            </thead>

            <tbody>
              {resultado.nodos.map((nodo) => (
                <tr key={nodo.id} className="border-t">
                  <td className="p-3">{nodo.tipo}</td>
                  <td className="p-3">{nodo.nombre}</td>
                  <td className="p-3">{nodo.ubicacion}</td>
                  <td className="p-3">{nodo.idExterno}</td>
                  <td className="p-3">
                    {nodo.activo ? "Activo" : "Inactivo"}
                  </td>
                  <td className="p-3">
                    {formatearFecha(
                      nodo.ultimaComunicacionExitosa
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  );
}

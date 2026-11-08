import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { listarNodos } from "@/lib/nodos-perifericos-backend";
import PanelAltaNodo from "./PanelAltaNodo";
import { requerirRol } from "@/lib/backoffice-sesion";

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
  await requerirRol("ADMINISTRADOR");

  const resultado = await listarNodos();

  return (
    <div className="w-full min-w-0 space-y-6 px-6 py-8 lg:px-8">
      <header>
        <h1 className="text-3xl font-semibold tracking-tight">
          Gestión de nodos periféricos
        </h1>

        <p className="mt-2 text-muted-foreground">
          Administración de balanzas y dispositivos de tracking.
        </p>
      </header>

      <PanelAltaNodo />

      {!resultado.ok ? (
        <div
          role="alert"
          className="rounded-xl border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive"
        >
          {resultado.mensaje}
        </div>
      ) : resultado.nodos.length === 0 ? (
        <div className="rounded-xl border border-dashed bg-card p-8 text-center text-muted-foreground">
          No hay nodos periféricos registrados.
        </div>
      ) : (
        <div className="overflow-hidden rounded-xl border bg-card shadow-sm">
          <Table>
            <TableHeader className="bg-muted/70">
              <TableRow className="hover:bg-transparent">
                {["Tipo", "Nombre", "Ubicación", "ID externo", "Estado", "Última comunicación"].map((titulo) => (
                  <TableHead key={titulo} className="px-3 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
                    {titulo}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>

            <TableBody>
              {resultado.nodos.map((nodo) => (
                <TableRow key={nodo.id}>
                  <TableCell className="px-3">{nodo.tipo}</TableCell>
                  <TableCell className="px-3 font-medium">{nodo.nombre}</TableCell>
                  <TableCell className="px-3">{nodo.ubicacion}</TableCell>
                  <TableCell className="px-3 tabular-nums">{nodo.idExterno}</TableCell>
                  <TableCell className="px-3">
                    <Badge
                      variant="secondary"
                      className={nodo.activo ? "bg-emerald-50 text-emerald-700" : "text-muted-foreground"}
                    >
                      {nodo.activo ? "Activo" : "Inactivo"}
                    </Badge>
                  </TableCell>
                  <TableCell className="px-3 text-muted-foreground">
                    {formatearFecha(nodo.ultimaComunicacionExitosa)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}

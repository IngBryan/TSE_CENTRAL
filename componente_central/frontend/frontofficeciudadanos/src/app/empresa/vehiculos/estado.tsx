import type { Vehiculo } from "./actions";

// yyyy-mm-dd -> dd/mm/yyyy
export const formatoFecha = (iso: string) => iso.split("-").reverse().join("/");

const etiqueta = "w-fit whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium";
const ok = `${etiqueta} bg-green-50 text-green-800 dark:bg-green-950 dark:text-green-200`;
const mal = `${etiqueta} bg-red-50 text-red-800 dark:bg-red-950 dark:text-red-200`;
const neutro = `${etiqueta} bg-muted text-foreground/80`;

// Habilitado = permiso vigente e ITV sin vencer (lo calcula el backend a la fecha de hoy)
export function Habilitacion({ vehiculo }: { vehiculo: Vehiculo }) {
  if (!vehiculo.activo) return <span className={neutro}>Dado de baja</span>;
  return vehiculo.habilitado ? <span className={ok}>Habilitado</span> : <span className={mal}>No habilitado</span>;
}

export function EstadoPermiso({ vehiculo }: { vehiculo: Vehiculo }) {
  const p = vehiculo.permiso;
  if (!p) return <span className="text-muted-foreground">Sin permiso</span>;
  return (
    <span className="flex flex-col gap-0.5">
      <span className="font-mono text-xs">{p.nro}</span>
      {p.vigente ? (
        <span>Vence {formatoFecha(p.fechaVencimiento)}</span>
      ) : (
        <span className="text-red-700 dark:text-red-300">
          Fuera de vigencia ({formatoFecha(p.fechaEmision)} – {formatoFecha(p.fechaVencimiento)})
        </span>
      )}
    </span>
  );
}

export function EstadoItv({ vehiculo }: { vehiculo: Vehiculo }) {
  const i = vehiculo.itv;
  if (!i) return <span className="text-muted-foreground">Sin ITV</span>;
  return (
    <span className={i.vigente ? "" : "text-red-700 dark:text-red-300"}>
      {i.vigente ? "Vence" : "Venció"} {formatoFecha(i.fechaVencimiento)}
    </span>
  );
}

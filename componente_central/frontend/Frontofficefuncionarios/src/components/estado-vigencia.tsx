import {
  ESTADOS_VEHICULO,
  PUNTO_DOCUMENTO,
  PUNTO_VEHICULO,
  estadoPeriodo,
  type EstadoVehiculo,
  type PeriodoVista,
} from "@/lib/vehiculos";

function Indicador({
  punto,
  children,
}: {
  punto: string;
  children: React.ReactNode;
}) {
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
      <span aria-hidden className={`size-2 shrink-0 rounded-full ${punto}`} />
      {children}
    </span>
  );
}

// Estado de un permiso nacional de circulacion o de una ITV: "Vigente", "Vence en 12 días", "Sin registro"...
export function EstadoPeriodo({ periodo }: { periodo: PeriodoVista }) {
  return (
    <span
      className={
        periodo.estado === "VIGENTE"
          ? "text-zinc-600 dark:text-zinc-400"
          : "text-zinc-800 dark:text-zinc-200"
      }
    >
      <Indicador punto={PUNTO_DOCUMENTO[periodo.estado]}>
        {estadoPeriodo(periodo)}
      </Indicador>
    </span>
  );
}

// Si el vehiculo puede circular hoy: activo, con el permiso vigente y una ITV sin vencer
export function EstadoVehiculoIndicador({
  estado,
}: {
  estado: EstadoVehiculo;
}) {
  return (
    <span
      className={
        estado === "DADO_DE_BAJA"
          ? "text-zinc-500"
          : "text-zinc-800 dark:text-zinc-200"
      }
    >
      <Indicador punto={PUNTO_VEHICULO[estado]}>
        {ESTADOS_VEHICULO[estado] ?? estado}
      </Indicador>
    </span>
  );
}

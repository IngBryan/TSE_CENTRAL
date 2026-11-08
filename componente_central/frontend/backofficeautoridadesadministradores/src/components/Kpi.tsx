import { ArrowDownRight, ArrowUpRight, Minus, type LucideIcon } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Card, CardAction, CardContent, CardDescription, CardHeader } from "@/components/ui/card";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Grupo, TablaKpi } from "@/lib/kpi";

export type FormatoKpi = "numero" | "porcentaje" | "toneladas";

type Props = {
  titulo: string;
  valor: number | null;
  // Value of the compared period; without it the KPI shows no variation
  comparativo?: number | null;
  formato?: FormatoKpi;
  // For KPIs where going up is bad (infractions): the arrow turns red when it rises
  mejorSiBaja?: boolean;
  tipo?: "numero" | "columnas" | "tabla";
  // Only for "columnas": one column per group
  agrupacion?: Grupo[];
  // Only for "tabla": several indicators side by side, one row per group; it shows no number of its own
  tabla?: TablaKpi;
  // Shown in a tinted badge next to the title
  icono?: LucideIcon;
  // Why the KPI could not be loaded, shown in place of its value
  error?: string;
};

const NUMERO = new Intl.NumberFormat("es-UY", { maximumFractionDigits: 1 });
const PORCENTAJE = new Intl.NumberFormat("es-UY", { style: "percent", maximumFractionDigits: 1 });

export function formatear(valor: number, formato: FormatoKpi): string {
  if (formato === "porcentaje") return PORCENTAJE.format(valor);
  if (formato === "toneladas") return `${NUMERO.format(valor)} t`;
  return NUMERO.format(valor);
}

export function Variacion({ valor, comparativo, formato, mejorSiBaja }: {
  valor: number;
  comparativo: number;
  formato: FormatoKpi;
  mejorSiBaja: boolean;
}) {
  // A rate is compared in percentage points; anything else, relative to the compared value
  const cambio = formato === "porcentaje" ? valor - comparativo : comparativo === 0 ? null : (valor - comparativo) / comparativo;

  if (cambio === null) {
    return <span className="text-xs text-muted-foreground">Sin base de comparación</span>;
  }

  const texto =
    formato === "porcentaje"
      ? `${NUMERO.format(Math.abs(cambio) * 100)} p.p.`
      : PORCENTAJE.format(Math.abs(cambio));
  return <BadgeCambio cambio={cambio} texto={texto} mejorSiBaja={mejorSiBaja} />;
}

// An arrow up or down, green when the change is good news
export function BadgeCambio({ cambio, texto, mejorSiBaja }: { cambio: number; texto: string; mejorSiBaja: boolean }) {
  if (cambio === 0) {
    return (
      <Badge variant="secondary" className="text-muted-foreground">
        <Minus /> {texto}
      </Badge>
    );
  }

  const sube = cambio > 0;
  const bueno = sube !== mejorSiBaja;
  const Flecha = sube ? ArrowUpRight : ArrowDownRight;

  return (
    <Badge
      variant="secondary"
      className={bueno ? "bg-emerald-50 text-emerald-700" : "bg-red-50 text-red-700"}
    >
      <Flecha aria-label={sube ? "Sube" : "Baja"} />
      {texto}
    </Badge>
  );
}

// The whole column height is the tooltip trigger, so short bars are easy to point at
function Barra({ alto, color, texto }: { alto: string; color: string; texto: string }) {
  return (
    <Tooltip>
      <TooltipTrigger
        aria-label={texto}
        render={<div className="group/barra flex h-full w-full max-w-4 cursor-default items-end" />}
      >
        <div
          className={`w-full rounded-t-sm ${color} transition-opacity group-hover/barra:opacity-75`}
          style={{ height: alto }}
        />
      </TooltipTrigger>
      <TooltipContent>{texto}</TooltipContent>
    </Tooltip>
  );
}

function Columnas({ grupos, formato, conComparativo }: { grupos: Grupo[]; formato: FormatoKpi; conComparativo: boolean }) {
  if (grupos.length === 0) {
    return <p className="py-10 text-center text-sm text-muted-foreground">Sin datos.</p>;
  }

  const maximo = Math.max(...grupos.flatMap((g) => [g.valor, g.comparativo ?? 0]), 1);
  const alto = (valor: number) => `${Math.max((valor / maximo) * 100, valor > 0 ? 2 : 0)}%`;

  return (
    <div className="space-y-2">
      <div className="flex h-36 items-end gap-1.5 border-b border-border">
        {grupos.map((grupo) => (
          <div key={grupo.clave} className="flex h-full min-w-0 flex-1 items-end justify-center gap-0.5">
            {conComparativo && (
              <Barra
                alto={alto(grupo.comparativo ?? 0)}
                color="bg-serie-comparativo"
                texto={`${grupo.etiqueta} · comparativo: ${formatear(grupo.comparativo ?? 0, formato)}`}
              />
            )}
            <Barra
              alto={alto(grupo.valor)}
              color="bg-serie-actual"
              texto={`${grupo.etiqueta}: ${formatear(grupo.valor, formato)}`}
            />
          </div>
        ))}
      </div>

      <div className="flex gap-1.5">
        {grupos.map((grupo) => (
          <span key={grupo.clave} className="min-w-0 flex-1 truncate text-center text-[10px] text-muted-foreground">
            {grupo.etiqueta}
          </span>
        ))}
      </div>

      {conComparativo && (
        <div className="flex gap-4 pt-1 text-[11px] text-muted-foreground">
          <span className="inline-flex items-center gap-1.5"><span className="size-2 rounded-sm bg-serie-actual" /> Período</span>
          <span className="inline-flex items-center gap-1.5">
            <span className="size-2 rounded-sm bg-serie-comparativo" /> Comparativo
          </span>
        </div>
      )}
    </div>
  );
}

// One row per group and one column per indicator, each value with its change against the comparison
function Tabla({ tabla }: { tabla: TablaKpi }) {
  if (tabla.filas.length === 0) {
    return <p className="py-10 text-center text-sm text-muted-foreground">Sin datos.</p>;
  }

  return (
    <div className="max-h-96 overflow-auto rounded-md border">
      <Table>
        <TableHeader className="sticky top-0 z-10 bg-card">
          <TableRow className="hover:bg-transparent">
            <TableHead className="px-3">{tabla.encabezado}</TableHead>
            {tabla.columnas.map((columna) => (
              <TableHead key={columna.id} className="px-3 text-right whitespace-normal">
                {columna.titulo}
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {tabla.filas.map((fila) => (
            <TableRow key={fila.clave}>
              <TableCell className="px-3 py-1.5 font-medium">{fila.etiqueta}</TableCell>
              {tabla.columnas.map((columna) => {
                const { valor, comparativo } = fila.valores[columna.id] ?? { valor: null, comparativo: null };
                return (
                  <TableCell key={columna.id} className="px-3 py-1.5 text-right tabular-nums">
                    <div className="flex items-center justify-end gap-2">
                      {valor === null ? "—" : formatear(valor, columna.formato)}
                      {valor !== null && comparativo !== null && (
                        <Variacion
                          valor={valor}
                          comparativo={comparativo}
                          formato={columna.formato}
                          mejorSiBaja={columna.mejorSiBaja}
                        />
                      )}
                    </div>
                  </TableCell>
                );
              })}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

export default function Kpi({
  titulo,
  valor,
  comparativo,
  formato = "numero",
  mejorSiBaja = false,
  tipo = "numero",
  agrupacion = [],
  tabla,
  icono: Icono,
  error,
}: Props) {
  const hayComparativo = valor !== null && comparativo !== null && comparativo !== undefined;

  return (
    <Card className="shadow-sm transition-shadow [--card-spacing:--spacing(5)] hover:shadow-md">
      <CardHeader>
        <CardDescription className="font-medium">{titulo}</CardDescription>
        {Icono && (
          <CardAction>
            <span className="flex size-9 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <Icono className="size-4.5" />
            </span>
          </CardAction>
        )}
      </CardHeader>

      <CardContent className="flex flex-col gap-3">
        {error ? (
          <p className="py-2 text-sm text-destructive">{error}</p>
        ) : tipo === "tabla" ? (
          tabla && <Tabla tabla={tabla} />
        ) : (
          <>
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <p className="text-3xl font-semibold tracking-tight tabular-nums">
            {valor === null ? "—" : formatear(valor, formato)}
          </p>
          {hayComparativo && (
            <Variacion valor={valor} comparativo={comparativo} formato={formato} mejorSiBaja={mejorSiBaja} />
          )}
        </div>

        {hayComparativo && (
          <p className="-mt-2 text-xs text-muted-foreground">vs {formatear(comparativo, formato)} en el comparativo</p>
        )}

        {tipo === "columnas" && (
          <div className="mt-2">
            <Columnas grupos={agrupacion} formato={formato} conComparativo={hayComparativo} />
          </div>
        )}
          </>
        )}
      </CardContent>
    </Card>
  );
}

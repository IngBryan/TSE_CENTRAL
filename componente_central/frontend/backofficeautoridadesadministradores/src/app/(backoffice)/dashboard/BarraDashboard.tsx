"use client";

import { Clock, SlidersHorizontal } from "lucide-react";
import { useState, type ReactNode } from "react";

import {
  OpcionesComparativo,
  OpcionesPeriodo,
  SelectorDepartamentos,
  SelectorTiposCarga,
  useEdicionFiltros,
} from "@/components/SelectoresFiltros";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import type { FiltrosDashboard, TipoCargaOpcion } from "@/lib/reportes";

type Props = {
  filtros: FiltrosDashboard;
  tiposCarga: TipoCargaOpcion[];
  hoy: string;
};

const ICONO =
  "relative inline-flex size-9 items-center justify-center rounded-lg border bg-card text-muted-foreground shadow-sm hover:bg-accent hover:text-accent-foreground data-popup-open:bg-accent data-popup-open:text-accent-foreground";

function Titulo({ children }: { children: ReactNode }) {
  return <h3 className="px-1 text-xs font-semibold tracking-wider text-muted-foreground uppercase">{children}</h3>;
}

// Clock: period and comparison of the dashboard. Sliders: the cargo type and department filters
export default function BarraDashboard({ filtros, tiposCarga, hoy }: Props) {
  const edicion = useEdicionFiltros(filtros, hoy, "/dashboard");
  const [abierto, setAbierto] = useState<"periodo" | "filtros" | null>(null);
  const activos = [filtros.tiposCarga, filtros.departamentos].filter(Boolean).length;

  const alternar = (popover: "periodo" | "filtros") => (abrir: boolean) => setAbierto(abrir ? popover : null);

  return (
    <div className="flex gap-2">
      <Popover open={abierto === "periodo"} onOpenChange={alternar("periodo")}>
        <PopoverTrigger className={ICONO} aria-label="Cambiar período" title="Cambiar período">
          <Clock className="size-4" />
        </PopoverTrigger>
        <PopoverContent align="end" className="max-h-[80vh] w-80 overflow-y-auto">
          <Titulo>Período</Titulo>
          <OpcionesPeriodo edicion={edicion} hoy={hoy} />
          <div className="border-t pt-2.5">
            <Titulo>Comparativo</Titulo>
          </div>
          <OpcionesComparativo edicion={edicion} />
          <Button type="button" disabled={edicion.incompleto} onClick={() => edicion.aplicar()} className="mt-1 h-9">
            Aplicar
          </Button>
        </PopoverContent>
      </Popover>

      <Popover open={abierto === "filtros"} onOpenChange={alternar("filtros")}>
        <PopoverTrigger className={ICONO} aria-label="Filtros" title="Filtros">
          <SlidersHorizontal className="size-4" />
          {activos > 0 && (
            <span className="absolute -top-1.5 -right-1.5 flex size-4 items-center justify-center rounded-full bg-primary text-[10px] text-primary-foreground">
              {activos}
            </span>
          )}
        </PopoverTrigger>
        <PopoverContent align="end" className="max-h-[80vh] w-80 overflow-y-auto">
          <Titulo>Tipo de carga</Titulo>
          <SelectorTiposCarga edicion={edicion} tiposCarga={tiposCarga} />
          <div className="border-t pt-2.5">
            <Titulo>Departamento</Titulo>
          </div>
          <SelectorDepartamentos edicion={edicion} />
          <div className="mt-1 flex gap-2">
            <Button type="button" onClick={() => edicion.aplicar()} className="h-9 flex-1">Aplicar</Button>
            <Button
              type="button"
              variant="outline"
              className="h-9"
              onClick={() => edicion.aplicar({ tiposCarga: undefined, departamentos: undefined })}
            >
              Quitar
            </Button>
          </div>
        </PopoverContent>
      </Popover>
    </div>
  );
}

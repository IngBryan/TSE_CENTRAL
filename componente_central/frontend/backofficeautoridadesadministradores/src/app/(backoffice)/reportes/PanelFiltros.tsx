"use client";

import { ChevronDown, ChevronsLeft, ChevronsRight, Plus, SlidersHorizontal, Trash2 } from "lucide-react";
import Link from "next/link";
import { useState, type ReactNode } from "react";

import {
  aRango,
  nombreTipoCarga,
  Opciones,
  OpcionesPeriodo,
  SelectorComparativo,
  SelectorDepartamentos,
  SelectorTiposCarga,
  textoPeriodo,
  useEdicionFiltros,
} from "@/components/SelectoresFiltros";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupLabel,
  SidebarHeader,
  useSidebar,
} from "@/components/ui/sidebar";
import { COMPARATIVOS, textoRango, textoSeleccion, type FiltrosDashboard, type TipoCargaOpcion } from "@/lib/reportes";
import {
  AGRUPACIONES,
  COMPARATIVO_POR_DEFECTO,
  consultaReporte,
  KPIS,
  MEDIDAS,
  usaComparativo,
  type Agrupacion,
  type IdKpi,
  type KpiReporte,
  type Medida,
  type Reporte,
} from "@/lib/reportes-kpis";

type Props = {
  filtros: FiltrosDashboard;
  reporte: Reporte;
  tiposCarga: TipoCargaOpcion[];
  // "YYYY-MM-DD" in Uruguay, computed by the server so both renders agree
  hoy: string;
};

const ORDEN_MEDIDAS = Object.keys(MEDIDAS) as Medida[];

function Seccion({ titulo, children }: { titulo: string; children: ReactNode }) {
  return (
    <SidebarGroup className="border-b border-sidebar-border p-0 pb-2">
      <SidebarGroupLabel className="px-5 pt-4 text-xs font-semibold tracking-wider uppercase">{titulo}</SidebarGroupLabel>
      {children}
    </SidebarGroup>
  );
}

type FilaFiltroProps = {
  nombre: string;
  valor: string;
  abierta: boolean;
  onAlternar: () => void;
  children: ReactNode;
};

// The row shows the name and current value; opening it reveals the selector
function FilaFiltro({ nombre, valor, abierta, onAlternar, children }: FilaFiltroProps) {
  return (
    <Collapsible open={abierta} onOpenChange={onAlternar}>
      <CollapsibleTrigger className="flex w-full items-center gap-3 px-5 py-2.5 text-left hover:bg-sidebar-accent">
        <span className="min-w-0 truncate text-sm font-medium" title={nombre}>{nombre}</span>
        <span className="ml-auto max-w-[45%] shrink-0 truncate text-xs text-muted-foreground">{valor}</span>
        <ChevronDown
          className={`size-4 shrink-0 text-muted-foreground transition-transform duration-200 ${abierta ? "rotate-180" : ""}`}
        />
      </CollapsibleTrigger>
      <CollapsibleContent className="space-y-2 px-5 pt-1 pb-4">{children}</CollapsibleContent>
    </Collapsible>
  );
}

function Subtitulo({ children }: { children: ReactNode }) {
  return <p className="pt-1 text-xs font-medium text-muted-foreground">{children}</p>;
}

// Folds the panel; it is the only thing showing while the panel is collapsed
function Plegar() {
  const { open, toggleSidebar } = useSidebar();
  const Icono = open ? ChevronsRight : ChevronsLeft;
  return (
    <Button
      type="button"
      variant="ghost"
      size="icon-sm"
      onClick={toggleSidebar}
      aria-label={open ? "Ocultar configuración" : "Mostrar configuración"}
      title={open ? "Ocultar configuración" : "Mostrar configuración"}
      className="text-muted-foreground"
    >
      <Icono />
    </Button>
  );
}

// On phones the panel is a sheet: this button, in the page header, opens it
export function BotonConfigurar() {
  const { toggleSidebar } = useSidebar();
  return (
    <Button type="button" variant="outline" className="h-9 gap-1.5 md:hidden" onClick={toggleSidebar}>
      <SlidersHorizontal className="size-4" /> Configurar
    </Button>
  );
}

export default function PanelFiltros({ filtros, reporte, tiposCarga, hoy }: Props) {
  const edicion = useEdicionFiltros(filtros, hoy, "/reportes");
  const [filaAbierta, setFilaAbierta] = useState<string | null>(null);
  const [agrupacion, setAgrupacion] = useState<Agrupacion>(reporte.agrupacion);
  const [kpis, setKpis] = useState<KpiReporte[]>(reporte.kpis);

  const alternarFila = (fila: string) => setFilaAbierta((actual) => (actual === fila ? null : fila));

  // At least one measure per KPI: the last one cannot be unchecked
  function alternarMedida(kpi: KpiReporte, medida: Medida) {
    const tiene = kpi.medidas.includes(medida);
    if (tiene && kpi.medidas.length === 1) return;
    const medidas = ORDEN_MEDIDAS.filter((m) => (m === medida ? !tiene : kpi.medidas.includes(m)));
    setKpis((lista) => lista.map((k) => (k.id === kpi.id ? { ...k, medidas } : k)));
  }

  function agregarKpi(id: IdKpi) {
    setKpis((lista) => [...lista, { id, medidas: ["actual"] }]);
    setFilaAbierta(`kpi-${id}`);
  }

  const disponibles = (Object.keys(KPIS) as IdKpi[]).filter((id) => !kpis.some((kpi) => kpi.id === id));

  // One comparison for the whole report: it shows up in Período as soon as a KPI needs it
  const compara = kpis.some(usaComparativo);
  const comparar = edicion.comparar || COMPARATIVO_POR_DEFECTO;
  const rangoComparar = aRango(edicion.rangoComparar);
  const incompleto =
    (edicion.periodo === "personalizado" && !aRango(edicion.rangoPropio)) ||
    (compara && comparar === "personalizado" && !rangoComparar);

  function aplicar() {
    if (incompleto) return;
    edicion.aplicar(
      compara ? { comparar, rangoComparar } : { comparar: undefined, rangoComparar: undefined },
      consultaReporte({ agrupacion, kpis })
    );
  }

  return (
    // Collapses to a rail with the arrow; on phones it is a sheet from the right. It starts below the top bar
    // of the layout (h-12, sticky above it): at top-0 that bar covered its header and the button to open it
    <Sidebar
      side="right"
      collapsible="icon"
      aria-label="Configuración del reporte"
      className="top-12! h-[calc(100svh-3rem)]! print:hidden"
    >
      <SidebarHeader className="flex-row items-center justify-between border-b border-sidebar-border px-3 group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:px-2">
        <span className="pl-2 text-sm font-semibold group-data-[collapsible=icon]:hidden">Configuración</span>
        <Plegar />
      </SidebarHeader>

      <SidebarContent className="gap-0 group-data-[collapsible=icon]:hidden">
        <Seccion titulo="KPIs">
          {kpis.map((kpi) => (
            <FilaFiltro
              key={kpi.id}
              nombre={KPIS[kpi.id].nombre}
              valor={kpi.medidas.map((m) => MEDIDAS[m]).join(" · ")}
              abierta={filaAbierta === `kpi-${kpi.id}`}
              onAlternar={() => alternarFila(`kpi-${kpi.id}`)}
            >
              <Subtitulo>Columnas</Subtitulo>
              <div className="space-y-0.5">
                {ORDEN_MEDIDAS.map((medida) => (
                  <Label
                    key={medida}
                    className="flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 font-normal hover:bg-sidebar-accent"
                  >
                    <Checkbox
                      checked={kpi.medidas.includes(medida)}
                      disabled={kpi.medidas.length === 1 && kpi.medidas.includes(medida)}
                      onCheckedChange={() => alternarMedida(kpi, medida)}
                    />
                    {MEDIDAS[medida]}
                  </Label>
                ))}
              </div>

              {usaComparativo(kpi) && (
                <p className="text-xs text-muted-foreground">El comparativo se elige en Período.</p>
              )}

              <Button
                type="button"
                variant="ghost"
                size="xs"
                disabled={kpis.length === 1}
                onClick={() => setKpis((lista) => lista.filter((k) => k.id !== kpi.id))}
                className="-ml-2 text-muted-foreground hover:text-destructive"
              >
                <Trash2 /> Quitar KPI
              </Button>
            </FilaFiltro>
          ))}

          {disponibles.length > 0 && (
            <div className="px-5 pt-1 pb-2">
              <Select value={null} onValueChange={(id) => id && agregarKpi(id as IdKpi)}>
                <SelectTrigger aria-label="Agregar KPI" className="w-full border-dashed text-muted-foreground">
                  <Plus className="size-4" />
                  <SelectValue placeholder="Agregar KPI…" />
                </SelectTrigger>
                <SelectContent>
                  {disponibles.map((id) => (
                    <SelectItem key={id} value={id}>{KPIS[id].nombre}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}
        </Seccion>

        <Seccion titulo="Período">
          <FilaFiltro
            nombre="Período"
            valor={textoPeriodo(edicion)}
            abierta={filaAbierta === "periodo"}
            onAlternar={() => alternarFila("periodo")}
          >
            <OpcionesPeriodo edicion={edicion} hoy={hoy} />
          </FilaFiltro>

          {compara && (
            <FilaFiltro
              nombre="Comparativo"
              valor={comparar === "personalizado" && rangoComparar ? textoRango(rangoComparar) : COMPARATIVOS[comparar]}
              abierta={filaAbierta === "comparativo"}
              onAlternar={() => alternarFila("comparativo")}
            >
              <SelectorComparativo
                comparar={comparar}
                onComparar={(opcion) => edicion.setComparar(opcion || COMPARATIVO_POR_DEFECTO)}
                rangoComparar={edicion.rangoComparar}
                onRangoComparar={edicion.setRangoComparar}
                rangoActual={edicion.rangoActual}
                conNinguno={false}
              />
            </FilaFiltro>
          )}
        </Seccion>

        <Seccion titulo="Agrupación">
          <FilaFiltro
            nombre="Agrupar por"
            valor={AGRUPACIONES[agrupacion]}
            abierta={filaAbierta === "agrupacion"}
            onAlternar={() => alternarFila("agrupacion")}
          >
            <Opciones
              nombre="Agrupar por"
              valor={agrupacion}
              onCambiar={setAgrupacion}
              opciones={(Object.keys(AGRUPACIONES) as Agrupacion[]).map((opcion) => ({
                valor: opcion,
                etiqueta: AGRUPACIONES[opcion],
              }))}
            />
          </FilaFiltro>
        </Seccion>

        <Seccion titulo="Filtros">
          <FilaFiltro
            nombre="Tipo de carga"
            valor={textoSeleccion(edicion.tiposCarga, nombreTipoCarga(tiposCarga))}
            abierta={filaAbierta === "tipoCarga"}
            onAlternar={() => alternarFila("tipoCarga")}
          >
            <SelectorTiposCarga edicion={edicion} tiposCarga={tiposCarga} />
          </FilaFiltro>

          <FilaFiltro
            nombre="Departamento"
            valor={textoSeleccion(edicion.departamentos, (d) => d)}
            abierta={filaAbierta === "departamento"}
            onAlternar={() => alternarFila("departamento")}
          >
            <SelectorDepartamentos edicion={edicion} />
          </FilaFiltro>
        </Seccion>
      </SidebarContent>

      <SidebarFooter className="flex-row gap-2 border-t border-sidebar-border p-4 group-data-[collapsible=icon]:hidden">
        <Button type="button" disabled={incompleto} onClick={aplicar} className="h-9 flex-1">
          Aplicar
        </Button>
        <Button variant="outline" className="h-9" nativeButton={false} render={<Link href="/reportes" />}>
          Limpiar
        </Button>
      </SidebarFooter>
    </Sidebar>
  );
}

"use client";

import { ChevronDown, ChevronsLeft, ChevronsRight, Plus, SlidersHorizontal, Trash2 } from "lucide-react";
import Link from "next/link";
import { useRef, useState, type ReactNode } from "react";

import {
  aRango,
  Opciones,
  OpcionesPeriodo,
  SelectorComparativo,
  SelectorValores,
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
import {
  COMPARATIVOS,
  nombreValor,
  seleccionesDe,
  textoRango,
  textoSeleccion,
  type FiltrosDashboard,
} from "@/lib/reportes";
import type { FacetaDisponible } from "@/lib/reportes-backend";
import {
  agrupacionPermitida,
  COMPARATIVO_POR_DEFECTO,
  consultaReporte,
  facetasDeFiltro,
  sinComparar,
  MEDIDAS,
  nombreFaceta,
  usaComparativo,
  type CatalogosFaceta,
  type IdKpi,
  type KpiReporte,
  type Medida,
  type Reporte,
} from "@/lib/reportes-kpis";
import { facetasPermitidas, valoresDeFaceta } from "./actions";

type Props = {
  filtros: FiltrosDashboard;
  reporte: Reporte;
  // The KPIs of the backend the front knows how to show, in its order
  kpisDisponibles: { id: IdKpi; nombre: string }[];
  errorKpis?: string;
  // The facets the KPIs of the report take, with their names, or null when the backend could not say
  facetas: FacetaDisponible[] | null;
  errorFacetas?: string;
  // The values of the catalog facets of `facetas`; the panel loads the ones a change of KPIs brings
  catalogos: CatalogosFaceta;
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

export default function PanelFiltros({
  filtros,
  reporte,
  kpisDisponibles,
  errorKpis,
  facetas: facetasIniciales,
  errorFacetas,
  catalogos: catalogosIniciales,
  hoy,
}: Props) {
  const edicion = useEdicionFiltros(filtros, hoy, "/reportes");
  const [filaAbierta, setFilaAbierta] = useState<string | null>(null);
  const [agrupacion, setAgrupacion] = useState(reporte.agrupacion);
  const [kpis, setKpis] = useState<KpiReporte[]>(reporte.kpis);
  const [facetas, setFacetas] = useState<FacetaDisponible[] | null>(facetasIniciales);
  const [errorPermitidas, setErrorPermitidas] = useState(errorFacetas);
  const [cargando, setCargando] = useState(false);
  const [catalogos, setCatalogos] = useState<CatalogosFaceta>(catalogosIniciales);
  // Only the answer to the last change counts: an earlier one may arrive later
  const ultimoPedido = useRef(0);

  const alternarFila = (fila: string) => setFilaAbierta((actual) => (actual === fila ? null : fila));

  // At least one measure per KPI: the last one cannot be unchecked
  function alternarMedida(kpi: KpiReporte, medida: Medida) {
    const tiene = kpi.medidas.includes(medida);
    if (tiene && kpi.medidas.length === 1) return;
    const medidas = ORDEN_MEDIDAS.filter((m) => (m === medida ? !tiene : kpi.medidas.includes(m)));
    setKpis((lista) => lista.map((k) => (k.id === kpi.id ? { ...k, medidas } : k)));
  }

  // Each KPI added or removed changes what the report can be grouped and filtered by: the backend says
  async function cambiarKpis(nuevos: KpiReporte[]) {
    setKpis(nuevos);
    const pedido = ++ultimoPedido.current;
    setCargando(true);
    const respuesta = await facetasPermitidas(nuevos.map((kpi) => kpi.id));
    if (pedido !== ultimoPedido.current) return;

    setCargando(false);
    if (!respuesta.ok) {
      setErrorPermitidas(respuesta.mensaje);
      return;
    }
    setFacetas(respuesta.datos);
    setErrorPermitidas(undefined);
    setAgrupacion((actual) => agrupacionPermitida(actual, respuesta.datos));
    cargarCatalogos(facetasDeFiltro(respuesta.datos).map((faceta) => faceta.id));
  }

  // Removing a KPI can bring back a filter whose values the page did not load
  function cargarCatalogos(ids: string[]) {
    for (const id of ids.filter((faceta) => !catalogos[faceta])) {
      valoresDeFaceta(id).then((respuesta) =>
        setCatalogos((actuales) => ({
          ...actuales,
          [id]: respuesta.ok ? { valores: respuesta.datos } : { valores: [], error: respuesta.mensaje },
        }))
      );
    }
  }

  function agregarKpi(id: IdKpi) {
    cambiarKpis([...kpis, { id, medidas: ["current"] }]);
    setFilaAbierta(`kpi-${id}`);
  }

  const nombreKpi = (id: IdKpi) => kpisDisponibles.find((kpi) => kpi.id === id)?.nombre ?? id;
  const disponibles = kpisDisponibles.filter((disponible) => !kpis.some((kpi) => kpi.id === disponible.id));
  const deFiltro = facetasDeFiltro(facetas ?? []);

  // One comparison for the whole report: it shows up in Período as soon as a KPI needs it
  // The whole history has nothing to be compared with: the KPIs only show their current value
  const historico = edicion.periodo === "historico";
  const medidasDe = (kpi: KpiReporte) => (historico ? sinComparar([kpi])[0].medidas : kpi.medidas);
  const compara = !historico && kpis.some(usaComparativo);
  const comparar = edicion.comparar || COMPARATIVO_POR_DEFECTO;
  const rangoComparar = aRango(edicion.rangoComparar);
  const incompleto =
    (edicion.periodo === "personalizado" && !aRango(edicion.rangoPropio)) ||
    (compara && comparar === "personalizado" && !rangoComparar);

  function aplicar() {
    if (incompleto || cargando) return;
    edicion.aplicar(
      {
        // A filter the KPIs do not take is dropped, even if it was set before the KPI that refuses it was added
        selecciones: seleccionesDe(edicion.selecciones, deFiltro.map((faceta) => faceta.id)),
        ...(compara ? { comparar, rangoComparar } : { comparar: undefined, rangoComparar: undefined }),
      },
      consultaReporte({ agrupacion, kpis: historico ? sinComparar(kpis) : kpis })
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
              nombre={nombreKpi(kpi.id)}
              valor={medidasDe(kpi).map((m) => MEDIDAS[m]).join(" · ")}
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
                      checked={medidasDe(kpi).includes(medida)}
                      disabled={historico || (kpi.medidas.length === 1 && kpi.medidas.includes(medida))}
                      onCheckedChange={() => alternarMedida(kpi, medida)}
                    />
                    {MEDIDAS[medida]}
                  </Label>
                ))}
              </div>

              {historico ? (
                <p className="text-xs text-muted-foreground">El histórico completo solo muestra el valor actual.</p>
              ) : (
                usaComparativo(kpi) && (
                  <p className="text-xs text-muted-foreground">El comparativo se elige en Período.</p>
                )
              )}

              <Button
                type="button"
                variant="ghost"
                size="xs"
                disabled={kpis.length === 1}
                onClick={() => cambiarKpis(kpis.filter((k) => k.id !== kpi.id))}
                className="-ml-2 text-muted-foreground hover:text-destructive"
              >
                <Trash2 /> Quitar KPI
              </Button>
            </FilaFiltro>
          ))}

          {errorKpis && <p className="px-5 pb-2 text-xs text-destructive">{errorKpis}</p>}

          {disponibles.length > 0 && (
            <div className="px-5 pt-1 pb-2">
              <Select value={null} onValueChange={(id) => id && agregarKpi(id as IdKpi)}>
                <SelectTrigger aria-label="Agregar KPI" className="w-full border-dashed text-muted-foreground">
                  <Plus className="size-4" />
                  <SelectValue placeholder="Agregar KPI…" />
                </SelectTrigger>
                <SelectContent>
                  {disponibles.map((kpi) => (
                    <SelectItem key={kpi.id} value={kpi.id}>{kpi.nombre}</SelectItem>
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
            <OpcionesPeriodo edicion={edicion} hoy={hoy} conHistorico />
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
          {errorPermitidas && <p className="px-5 pt-1 text-xs text-destructive">{errorPermitidas}</p>}
          <FilaFiltro
            nombre="Agrupar por"
            valor={nombreFaceta(facetas, agrupacion)}
            abierta={filaAbierta === "agrupacion"}
            onAlternar={() => alternarFila("agrupacion")}
          >
            <Opciones
              nombre="Agrupar por"
              valor={agrupacion}
              onCambiar={setAgrupacion}
              opciones={(facetas ?? []).map((faceta) => ({ valor: faceta.id, etiqueta: faceta.name }))}
            />
          </FilaFiltro>
        </Seccion>

        <Seccion titulo="Filtros">
          {deFiltro.map((faceta) => {
            const catalogo = catalogos[faceta.id];
            const seleccion = edicion.seleccion(faceta.id);
            return (
              <FilaFiltro
                key={faceta.id}
                nombre={faceta.name}
                valor={textoSeleccion(seleccion, nombreValor(catalogo?.valores ?? []))}
                abierta={filaAbierta === `filtro-${faceta.id}`}
                onAlternar={() => alternarFila(`filtro-${faceta.id}`)}
              >
                {!catalogo ? (
                  <p className="text-xs text-muted-foreground">Cargando valores…</p>
                ) : catalogo.error ? (
                  <p className="text-xs text-destructive">{catalogo.error}</p>
                ) : (
                  <SelectorValores
                    nombre={faceta.name}
                    valores={catalogo.valores}
                    seleccion={seleccion}
                    onCambiar={(nueva) => edicion.setSeleccion(faceta.id, nueva)}
                  />
                )}
              </FilaFiltro>
            );
          })}
        </Seccion>
      </SidebarContent>

      <SidebarFooter className="flex-row gap-2 border-t border-sidebar-border p-4 group-data-[collapsible=icon]:hidden">
        <Button type="button" disabled={incompleto || cargando} onClick={aplicar} className="h-9 flex-1">
          {cargando ? "Actualizando…" : "Aplicar"}
        </Button>
        <Button variant="outline" className="h-9" nativeButton={false} render={<Link href="/reportes" />}>
          Limpiar
        </Button>
      </SidebarFooter>
    </Sidebar>
  );
}

"use client";

import { Ban, Check } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import type { DateRange } from "react-day-picker";
import { es } from "react-day-picker/locale";

import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Toggle } from "@/components/ui/toggle";
import {
  COMPARATIVOS,
  consultaFiltros,
  DEPARTAMENTOS,
  PERIODOS,
  rangoComparacion,
  rangoPeriodo,
  seleccionVacia,
  textoRango,
  type Comparativo,
  type FiltrosDashboard,
  type Periodo,
  type Rango,
  type Seleccion,
  type TipoCargaOpcion,
} from "@/lib/reportes";

const SIN_SELECCION: Seleccion = { incluir: [], excluir: [] };

// The calendar works with local Dates; the filters with "YYYY-MM-DD"
function aDate(fecha: string): Date {
  const [anio, mes, dia] = fecha.split("-").map(Number);
  return new Date(anio, mes - 1, dia);
}

function aTexto(fecha: Date): string {
  const mes = String(fecha.getMonth() + 1).padStart(2, "0");
  const dia = String(fecha.getDate()).padStart(2, "0");
  return `${fecha.getFullYear()}-${mes}-${dia}`;
}

export function aDateRange(rango: Rango | undefined): DateRange | undefined {
  return rango && { from: aDate(rango.desde), to: aDate(rango.hasta) };
}

export function aRango(seleccion: DateRange | undefined): Rango | undefined {
  return seleccion?.from && seleccion.to ? { desde: aTexto(seleccion.from), hasta: aTexto(seleccion.to) } : undefined;
}

/**
 * Filters being edited before they are applied, starting from the ones in the URL. `aplicar` navigates to
 * `ruta` with them; the page reads them back from the URL.
 */
export function useEdicionFiltros(filtros: FiltrosDashboard, hoy: string, ruta: string) {
  const router = useRouter();
  const [periodo, setPeriodo] = useState<Periodo>(filtros.periodo);
  const [rangoPropio, setRangoPropio] = useState<DateRange | undefined>(aDateRange(filtros.rango));
  const [comparar, setComparar] = useState<Comparativo | "">(filtros.comparar ?? "");
  const [rangoComparar, setRangoComparar] = useState<DateRange | undefined>(aDateRange(filtros.rangoComparar));
  const [tiposCarga, setTiposCarga] = useState<Seleccion>(filtros.tiposCarga ?? SIN_SELECCION);
  const [departamentos, setDepartamentos] = useState<Seleccion>(filtros.departamentos ?? SIN_SELECCION);

  const rangoElegido = aRango(rangoPropio);
  const rangoCompararElegido = aRango(rangoComparar);
  const rangoActual = rangoPeriodo(periodo, rangoElegido, hoy);
  const comparacion = comparar ? rangoComparacion(comparar, rangoActual, rangoCompararElegido) : null;

  // A custom range needs both ends before it can be applied
  const incompleto =
    (periodo === "personalizado" && !rangoElegido) || (comparar === "personalizado" && !rangoCompararElegido);

  const elegidos: FiltrosDashboard = {
    periodo,
    rango: periodo === "personalizado" ? rangoElegido : undefined,
    comparar: comparar || undefined,
    rangoComparar: comparar === "personalizado" ? rangoCompararElegido : undefined,
    tiposCarga: seleccionVacia(tiposCarga) ? undefined : tiposCarga,
    departamentos: seleccionVacia(departamentos) ? undefined : departamentos,
  };

  return {
    periodo, setPeriodo, rangoPropio, setRangoPropio,
    comparar, setComparar, rangoComparar, setRangoComparar,
    tiposCarga, setTiposCarga, departamentos, setDepartamentos,
    rangoActual, comparacion, incompleto,
    // extra: more query parameters of the page (the report KPIs and grouping)
    aplicar: (cambios: Partial<FiltrosDashboard> = {}, extra = "") => {
      // Checked on what is applied: a half-picked custom comparison does not block a page that drops it
      const final = { ...elegidos, ...cambios };
      const falta =
        (final.periodo === "personalizado" && !final.rango) ||
        (final.comparar === "personalizado" && !final.rangoComparar);
      if (!falta) router.push(`${ruta}?${consultaFiltros(final)}${extra ? `&${extra}` : ""}`);
    },
  };
}

export type EdicionFiltros = ReturnType<typeof useEdicionFiltros>;

type OpcionesProps<T extends string> = {
  nombre: string;
  opciones: { valor: T; etiqueta: string; detalle?: string }[];
  valor: T;
  onCambiar: (valor: T) => void;
};

// A list of exclusive choices, each with its dates on the right
export function Opciones<T extends string>({ nombre, opciones, valor, onCambiar }: OpcionesProps<T>) {
  return (
    <RadioGroup aria-label={nombre} value={valor} onValueChange={(v) => onCambiar(v as T)} className="gap-0.5">
      {opciones.map((opcion) => (
        <Label
          key={opcion.valor}
          className="flex cursor-pointer items-center gap-2.5 rounded-md px-2 py-1.5 font-normal hover:bg-accent has-data-checked:bg-accent has-data-checked:font-medium"
        >
          <RadioGroupItem value={opcion.valor} />
          <span>{opcion.etiqueta}</span>
          {opcion.detalle && (
            <span className="ml-auto text-[11px] font-normal text-muted-foreground tabular-nums">{opcion.detalle}</span>
          )}
        </Label>
      ))}
    </RadioGroup>
  );
}

function SelectorRango({ valor, onCambiar }: { valor?: DateRange; onCambiar: (rango?: DateRange) => void }) {
  return (
    <div className="space-y-1 pt-1">
      <Calendar
        mode="range"
        locale={es}
        selected={valor}
        onSelect={onCambiar}
        defaultMonth={valor?.from}
        className="rounded-md border bg-transparent"
      />
      {!(valor?.from && valor.to) && (
        <p className="text-xs text-muted-foreground">Elegí el día de inicio y el de fin.</p>
      )}
    </div>
  );
}

export function textoPeriodo(edicion: EdicionFiltros): string {
  return edicion.periodo === "personalizado" ? textoRango(edicion.rangoActual) : PERIODOS[edicion.periodo];
}

export function textoComparativo(edicion: EdicionFiltros): string {
  if (edicion.comparacion) return textoRango(edicion.comparacion);
  return edicion.comparar ? COMPARATIVOS[edicion.comparar] : "Ninguno";
}

export function OpcionesPeriodo({ edicion, hoy }: { edicion: EdicionFiltros; hoy: string }) {
  return (
    <>
      <Opciones
        nombre="Período"
        valor={edicion.periodo}
        onCambiar={edicion.setPeriodo}
        opciones={(Object.keys(PERIODOS) as Periodo[]).map((opcion) => ({
          valor: opcion,
          etiqueta: PERIODOS[opcion],
          detalle: opcion === "personalizado" ? undefined : textoRango(rangoPeriodo(opcion, undefined, hoy)),
        }))}
      />
      {edicion.periodo === "personalizado" && (
        <SelectorRango valor={edicion.rangoPropio} onCambiar={edicion.setRangoPropio} />
      )}
    </>
  );
}

type SelectorComparativoProps = {
  comparar: Comparativo | "";
  onComparar: (comparar: Comparativo | "") => void;
  rangoComparar?: DateRange;
  onRangoComparar: (rango?: DateRange) => void;
  // The period being compared, to show the dates of each option
  rangoActual: Rango;
  // Without it there is always a comparison (a report KPI that shows the previous value needs one)
  conNinguno?: boolean;
};

export function SelectorComparativo({
  comparar,
  onComparar,
  rangoComparar,
  onRangoComparar,
  rangoActual,
  conNinguno = true,
}: SelectorComparativoProps) {
  const opciones: { valor: Comparativo | ""; etiqueta: string; detalle?: string }[] = [
    ...(conNinguno ? [{ valor: "" as const, etiqueta: "Ninguno" }] : []),
    ...(Object.keys(COMPARATIVOS) as Comparativo[]).map((opcion) => {
      const rango = opcion === "personalizado" ? null : rangoComparacion(opcion, rangoActual, undefined);
      return { valor: opcion, etiqueta: COMPARATIVOS[opcion], detalle: rango ? textoRango(rango) : undefined };
    }),
  ];

  return (
    <>
      <Opciones nombre="Comparativo" valor={comparar} onCambiar={onComparar} opciones={opciones} />
      {comparar === "personalizado" && <SelectorRango valor={rangoComparar} onCambiar={onRangoComparar} />}
    </>
  );
}

export function OpcionesComparativo({ edicion }: { edicion: EdicionFiltros }) {
  return (
    <SelectorComparativo
      comparar={edicion.comparar}
      onComparar={edicion.setComparar}
      rangoComparar={edicion.rangoComparar}
      onRangoComparar={edicion.setRangoComparar}
      rangoActual={edicion.rangoActual}
    />
  );
}

type SelectorMultipleProps = {
  nombre: string;
  opciones: { valor: string; nombre: string }[];
  seleccion: Seleccion;
  onCambiar: (seleccion: Seleccion) => void;
};

type Estado = "incluido" | "excluido" | null;

// Each value is included, excluded or left alone, independently: "Soja and Trigo, but not Arena" is one filter
function SelectorMultiple({ nombre, opciones, seleccion, onCambiar }: SelectorMultipleProps) {
  const estado = (valor: string): Estado =>
    seleccion.incluir.includes(valor) ? "incluido" : seleccion.excluir.includes(valor) ? "excluido" : null;

  // Pressing the state a value already has clears it; the other state replaces it
  function marcar(valor: string, nuevo: Exclude<Estado, null>) {
    const actual = estado(valor);
    const sin = (lista: string[]) => lista.filter((v) => v !== valor);
    const ordenar = (lista: string[]) => opciones.map((o) => o.valor).filter((v) => lista.includes(v));
    const incluir = sin(seleccion.incluir);
    const excluir = sin(seleccion.excluir);
    if (actual !== nuevo) (nuevo === "incluido" ? incluir : excluir).push(valor);
    onCambiar({ incluir: ordenar(incluir), excluir: ordenar(excluir) });
  }

  const resumen = [
    seleccion.incluir.length && `${seleccion.incluir.length} incluidos`,
    seleccion.excluir.length && `${seleccion.excluir.length} excluidos`,
  ].filter(Boolean);

  return (
    <div className="space-y-2">
      <ul aria-label={nombre} className="max-h-60 space-y-0.5 overflow-y-auto rounded-md border p-1">
        {opciones.map((opcion) => {
          const actual = estado(opcion.valor);
          return (
            <li
              key={opcion.valor}
              className={`flex items-center gap-1 rounded px-2 py-0.5 text-sm ${
                actual === "incluido" ? "bg-primary/8" : actual === "excluido" ? "bg-destructive/6" : ""
              }`}
            >
              <span
                className={`min-w-0 flex-1 truncate ${
                  actual === "incluido"
                    ? "font-medium text-primary"
                    : actual === "excluido"
                      ? "text-muted-foreground line-through"
                      : ""
                }`}
              >
                {opcion.nombre}
              </span>
              <Toggle
                size="sm"
                aria-label={`Incluir ${opcion.nombre}`}
                title="Incluir"
                pressed={actual === "incluido"}
                onPressedChange={() => marcar(opcion.valor, "incluido")}
                className="size-7 min-w-7 px-0 text-muted-foreground aria-pressed:bg-primary aria-pressed:text-primary-foreground"
              >
                <Check />
              </Toggle>
              <Toggle
                size="sm"
                aria-label={`Excluir ${opcion.nombre}`}
                title="Excluir"
                pressed={actual === "excluido"}
                onPressedChange={() => marcar(opcion.valor, "excluido")}
                className="size-7 min-w-7 px-0 text-muted-foreground aria-pressed:bg-destructive aria-pressed:text-white"
              >
                <Ban />
              </Toggle>
            </li>
          );
        })}
      </ul>

      <div className="flex items-center justify-between text-xs text-muted-foreground">
        <span>{resumen.length ? resumen.join(" · ") : "Sin marcar: se incluyen todos"}</span>
        {resumen.length > 0 && (
          <Button type="button" variant="link" size="xs" className="h-auto p-0" onClick={() => onCambiar(SIN_SELECCION)}>
            Limpiar
          </Button>
        )}
      </div>
    </div>
  );
}

export function SelectorTiposCarga({ edicion, tiposCarga }: { edicion: EdicionFiltros; tiposCarga: TipoCargaOpcion[] }) {
  return (
    <SelectorMultiple
      nombre="Tipo de carga"
      opciones={tiposCarga.map((t) => ({ valor: String(t.id), nombre: t.nombre }))}
      seleccion={edicion.tiposCarga}
      onCambiar={edicion.setTiposCarga}
    />
  );
}

export function SelectorDepartamentos({ edicion }: { edicion: EdicionFiltros }) {
  return (
    <SelectorMultiple
      nombre="Departamento"
      opciones={DEPARTAMENTOS.map((d) => ({ valor: d, nombre: d }))}
      seleccion={edicion.departamentos}
      onCambiar={edicion.setDepartamentos}
    />
  );
}

export function nombreTipoCarga(tiposCarga: TipoCargaOpcion[]): (id: string) => string {
  return (id) => tiposCarga.find((t) => String(t.id) === id)?.nombre ?? id;
}

"use client";

import { FileDown } from "lucide-react";
import { useState } from "react";

import { formatear } from "@/components/Kpi";
import { Button } from "@/components/ui/button";
import { fechaCorta } from "@/lib/reportes";
import type { FilaReporte, TablaReporte } from "@/lib/reportes-tabla";
import {
  KPIS,
  MEDIDAS,
  nombreFaceta,
  nombreKpi,
  type IdKpi,
  type Medida,
  type NombresKpi,
  type Reporte,
} from "@/lib/reportes-kpis";
import type { FacetaDisponible } from "@/lib/reportes-backend";

type Props = {
  nombres: NombresKpi;
  facetas: FacetaDisponible[] | null;
  tabla: TablaReporte;
  reporte: Reporte;
  hoy: string;
  // The same sentence and filters as the page header, for the PDF
  descripcion: string;
  filtros: string[];
};

const NUMERO = new Intl.NumberFormat("es-UY", { maximumFractionDigits: 1 });

function columnas(reporte: Reporte) {
  return reporte.kpis.flatMap((kpi) => kpi.medidas.map((medida) => ({ id: kpi.id, medida })));
}

function descargar(contenido: Blob, nombre: string) {
  const url = URL.createObjectURL(contenido);
  const enlace = document.createElement("a");
  enlace.href = url;
  enlace.download = nombre;
  enlace.click();
  URL.revokeObjectURL(url);
}

// ---- CSV: raw numbers, so a spreadsheet can work with them ----

function unidad(id: IdKpi, medida: Medida): string {
  const esTasa = KPIS[id].formato === "porcentaje";
  if (medida === "growth") return " (%)";
  if (medida === "difference") return esTasa ? " (p.p.)" : KPIS[id].formato === "toneladas" ? " (t)" : "";
  return esTasa ? " (%)" : KPIS[id].formato === "toneladas" ? " (t)" : "";
}

// Rates and growth as percentages with two decimals
function valorCsv(fila: FilaReporte, id: IdKpi, medida: Medida): string {
  const valor = fila.valores[id]?.[medida];
  if (valor === undefined || valor === null) return "";
  const enPorcentaje = medida === "growth" || KPIS[id].formato === "porcentaje";
  return enPorcentaje ? (valor * 100).toFixed(2) : valor.toFixed(KPIS[id].formato === "toneladas" ? 1 : 0);
}

function celdaCsv(texto: string): string {
  return /[",\n]/.test(texto) ? `"${texto.replaceAll('"', '""')}"` : texto;
}

// One line per group, then the total; every row, not just the page on screen
function generarCsv({ tabla, reporte, nombres, facetas }: Props): string {
  const encabezado = [
    nombreFaceta(facetas, reporte.agrupacion),
    ...columnas(reporte).map(({ id, medida }) => `${nombreKpi(nombres, id)} - ${MEDIDAS[medida]}${unidad(id, medida)}`),
  ];
  const lineas = [...tabla.filas, tabla.total].map((fila) => [
    fila.etiqueta,
    ...columnas(reporte).map(({ id, medida }) => valorCsv(fila, id, medida)),
  ]);
  return [encabezado, ...lineas].map((linea) => linea.map(celdaCsv).join(",")).join("\r\n");
}

// ---- PDF: the table as on screen, formatted ----

type Tono = "bueno" | "malo" | null;

// Text of a cell and whether its change is good or bad news (green or red)
function valorPdf(fila: FilaReporte, id: IdKpi, medida: Medida): [string, Tono] {
  const valor = fila.valores[id]?.[medida];
  const { formato, mejorSiBaja } = KPIS[id];
  if (valor === undefined || valor === null) return ["—", null];
  if (medida === "current" || medida === "previous") return [formatear(valor, formato), null];
  if (valor === 0) return ["0", null];

  const signo = valor > 0 ? "+" : "-";
  const texto =
    medida === "growth"
      ? `${signo}${NUMERO.format(Math.abs(valor) * 100)} %`
      : formato === "porcentaje"
        ? `${signo}${NUMERO.format(Math.abs(valor) * 100)} p.p.`
        : `${signo}${formatear(Math.abs(valor), formato)}`;
  return [texto, valor > 0 !== mejorSiBaja ? "bueno" : "malo"];
}

async function generarPdf({ tabla, reporte, nombres, facetas, hoy, descripcion, filtros }: Props): Promise<Blob> {
  const [{ jsPDF }, { autoTable }] = await Promise.all([import("jspdf"), import("jspdf-autotable")]);

  const doc = new jsPDF({ orientation: "landscape", unit: "mm", format: "a4" });
  const margen = 12;
  const ancho = doc.internal.pageSize.getWidth() - margen * 2;

  doc.setFontSize(16);
  doc.setFont("helvetica", "bold");
  doc.text("Reportes", margen, margen + 4);

  doc.setFont("helvetica", "normal");
  doc.setFontSize(9.5);
  doc.setTextColor(80);
  let y = margen + 10;
  for (const linea of [descripcion, filtros.length ? `Filtros: ${filtros.join(" · ")}` : null]) {
    if (!linea) continue;
    const renglones = doc.splitTextToSize(linea, ancho);
    doc.text(renglones, margen, y);
    y += renglones.length * 4.2;
  }
  doc.setFontSize(8);
  doc.text(`CARGA.UY · Emitido el ${fechaCorta(hoy)}`, margen, y);

  const cols = columnas(reporte);
  const filas = [...tabla.filas, tabla.total].map((fila) => [
    fila.etiqueta,
    ...cols.map(({ id, medida }) => valorPdf(fila, id, medida)),
  ]);

  autoTable(doc, {
    startY: y + 4,
    margin: { left: margen, right: margen, bottom: margen + 4 },
    styles: { fontSize: 8, cellPadding: 1.5, lineColor: 225, lineWidth: 0.1 },
    headStyles: { fillColor: [244, 244, 245], textColor: 30, fontStyle: "bold", valign: "bottom" },
    footStyles: { fillColor: [244, 244, 245], textColor: 30, fontStyle: "bold" },
    columnStyles: Object.fromEntries(cols.map((_, i) => [i + 1, { halign: "right" as const }])),
    head: [
      [
        { content: nombreFaceta(facetas, reporte.agrupacion), rowSpan: 2 },
        ...reporte.kpis.map((kpi) => ({ content: nombreKpi(nombres, kpi.id), colSpan: kpi.medidas.length })),
      ],
      cols.map(({ medida }) => ({ content: MEDIDAS[medida], styles: { halign: "right" as const, fontSize: 7 } })),
    ],
    body: filas.slice(0, -1).map((fila) => fila.map((celda) => (typeof celda === "string" ? celda : celda[0]))),
    foot: [filas.at(-1)!.map((celda) => (typeof celda === "string" ? celda : celda[0]))],
    // Growth and difference in green or red, as on screen
    didParseCell: (datos) => {
      if (datos.column.index === 0 || datos.section === "head") return;
      const fila = datos.section === "foot" ? filas.at(-1)! : filas[datos.row.index];
      const celda = fila[datos.column.index];
      const tono = typeof celda === "string" ? null : celda[1];
      if (tono) datos.cell.styles.textColor = tono === "bueno" ? [21, 128, 61] : [185, 28, 28];
    },
    didDrawPage: () => {
      doc.setFontSize(7.5);
      doc.setTextColor(140);
      const pagina = `Página ${doc.getCurrentPageInfo().pageNumber}`;
      doc.text(pagina, doc.internal.pageSize.getWidth() - margen, doc.internal.pageSize.getHeight() - margen + 2, { align: "right" });
    },
  });

  return doc.output("blob");
}

export default function ExportarReporte(props: Props) {
  const [generando, setGenerando] = useState(false);
  const nombre = `reporte-${props.reporte.agrupacion}-${props.hoy}`;

  async function descargarPdf() {
    setGenerando(true);
    try {
      descargar(await generarPdf(props), `${nombre}.pdf`);
    } finally {
      setGenerando(false);
    }
  }

  return (
    <div className="flex gap-2 print:hidden">
      <Button
        type="button"
        variant="outline"
        className="h-9 gap-1.5"
        // The BOM lets Excel read the accents as UTF-8
        onClick={() => descargar(new Blob(["﻿", generarCsv(props)], { type: "text/csv;charset=utf-8" }), `${nombre}.csv`)}
      >
        <FileDown className="size-4" /> CSV
      </Button>
      <Button type="button" variant="outline" className="h-9 gap-1.5" disabled={generando} onClick={descargarPdf}>
        <FileDown className="size-4" /> {generando ? "Generando…" : "PDF"}
      </Button>
    </div>
  );
}

import { randomUUID } from "node:crypto";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import { tipoInfraccion } from "@/lib/infracciones";
import { TABLAS, type Estadisticas, type FilaPublica, type Tabla } from "@/lib/portal";

// Descarga en CSV de una tabla del portal publico, como dato abierto: ?tabla=meses|rubros|tipos-carga|empresas-rubro|infracciones.
// Separador ";" y BOM UTF-8, para que Excel en es-UY lo abra con las columnas y las tildes bien. Lo reservado
// por el umbral de agregacion sale como "Reservado", igual que en la pagina.
const COLUMNAS: Record<Tabla, string[]> = {
  meses: ["Mes", "Viajes", "Kg transportados"],
  rubros: ["Rubro", "Viajes", "Kg transportados"],
  "tipos-carga": ["Tipo de carga", "Viajes", "Kg transportados"],
  "empresas-rubro": ["Rubro", "Empresas habilitadas"],
  infracciones: ["Tipo", "Infracciones confirmadas"],
};

export async function GET(request: Request) {
  const url = new URL(request.url);
  const tabla = url.searchParams.get("tabla") as Tabla;
  const anio = url.searchParams.get("anio") ?? "";
  if (!(tabla in TABLAS) || (anio && !/^\d{4}$/.test(anio))) {
    return new Response("Tabla o año no válidos", { status: 400 });
  }

  let datos: Estadisticas;
  try {
    const res = await llamarBackend<Estadisticas>(`/publico/estadisticas${anio ? `?anio=${anio}` : ""}`, {
      correlationId: randomUUID(),
    });
    if (res.status !== 200) return new Response("Año no válido", { status: 400 });
    datos = res.data;
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return new Response("El servicio no está disponible", { status: 503 });
  }

  const conKg = tabla !== "infracciones" && tabla !== "empresas-rubro";
  const fila = (f: FilaPublica) => {
    const etiqueta =
      tabla === "infracciones"
        ? tipoInfraccion(f.etiqueta)
        : tabla === "meses"
          ? f.etiqueta.charAt(0).toUpperCase() + f.etiqueta.slice(1)
          : f.etiqueta;
    if (f.reservado) return [etiqueta, "Reservado", ...(conKg ? ["Reservado"] : [])];
    return [etiqueta, String(f.cantidad), ...(conKg ? [String(f.kg)] : [])];
  };
  const lineas = [COLUMNAS[tabla], ...datos[TABLAS[tabla]].map(fila)].map((l) => l.map(celda).join(";"));

  return new Response("﻿" + lineas.join("\r\n") + "\r\n", {
    headers: {
      "Content-Type": "text/csv; charset=utf-8",
      "Content-Disposition": `attachment; filename="transporte-carga-${tabla}-${datos.anio}.csv"`,
      "Cache-Control": "no-store",
    },
  });
}

// Comillas solo si hace falta (separador, comillas o salto de linea). Por las dudas (datos viejos de texto libre):
// si empieza como una formula (=, +, -, @) se le antepone un apostrofo para que Excel no la ejecute.
function celda(texto: string) {
  const seguro = /^[=+\-@\t\r]/.test(texto) ? `'${texto}` : texto;
  return /[;"\r\n]/.test(seguro) ? `"${seguro.replace(/"/g, '""')}"` : seguro;
}

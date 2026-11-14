"use client";

import { useState } from "react";
import { fechaHora, type EventoTrackingVista } from "@/lib/casos";

const TAMANIO_PAGINA = 20;

/** Presentación únicamente: el expediente y sus eventos quedan intactos. */
export function EventosTrackingPaginados({ eventos }: { eventos: EventoTrackingVista[] }) {
  const [pagina, setPagina] = useState(1);
  const totalPaginas = Math.max(1, Math.ceil(eventos.length / TAMANIO_PAGINA));
  const paginaActual = Math.min(pagina, totalPaginas);
  const inicio = (paginaActual - 1) * TAMANIO_PAGINA;
  const visibles = eventos.slice(inicio, inicio + TAMANIO_PAGINA);
  return (
    <div className="rounded-xl border bg-card p-6 text-sm shadow-sm">
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="font-medium text-foreground">Eventos de tracking asociados</h3>
        <span className="text-xs text-muted-foreground">{eventos.length} eventos GPS vinculados al caso</span>
      </div>
      {eventos.length === 0 ? <p role="status" className="rounded-lg bg-background p-4 text-muted-foreground">
        Aún no hay eventos GPS asociados. Si el caso recibe nuevos eventos, aparecerán aquí sin recargar la página.
      </p> : <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="border-b border-border text-muted-foreground">
            <tr><th scope="col" className="py-2 pr-3 font-medium">N.º</th>
              <th scope="col" className="py-2 pr-3 font-medium">Matrícula</th>
              <th scope="col" className="py-2 font-medium">Fecha y hora de medición</th></tr>
          </thead>
          <tbody>{visibles.map((evento, indice) => (
            <tr key={`${inicio + indice}-${evento.matricula}-${evento.fechaHora}`} className="border-b border-border">
              <td className="py-2 pr-3 tabular-nums">{inicio + indice + 1}</td>
              <td className="py-2 pr-3 font-mono">{evento.matricula}</td>
              <td className="py-2 tabular-nums">{fechaHora(evento.fechaHora)}</td>
            </tr>
          ))}</tbody>
        </table>
      </div>}
      <nav className="mt-3 flex flex-wrap items-center justify-between gap-2" aria-label="Paginación de eventos GPS">
        <span className="text-xs text-muted-foreground">{eventos.length === 0 ? 0 : inicio + 1}–{inicio + visibles.length} de {eventos.length} · Página {paginaActual} de {totalPaginas}</span>
        <div className="flex gap-2">
          <button type="button" disabled={paginaActual === 1} onClick={() => setPagina(paginaActual - 1)}
            className="rounded-lg border px-3 py-1.5 text-xs hover:bg-accent disabled:opacity-40">Anterior</button>
          <button type="button" disabled={paginaActual === totalPaginas} onClick={() => setPagina(paginaActual + 1)}
            className="rounded-lg border px-3 py-1.5 text-xs hover:bg-accent disabled:opacity-40">Siguiente</button>
        </div>
      </nav>
    </div>
  );
}

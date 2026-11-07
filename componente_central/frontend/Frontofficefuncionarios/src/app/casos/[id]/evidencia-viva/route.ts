import { randomUUID } from "node:crypto";
import { NextResponse } from "next/server";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import type { EvidenciaVista } from "@/lib/casos";

/** Snapshot de las dos listas que crecen durante una fiscalización.
 * Usa la sesión y la autorización del backend: no expone X-Api-Key en el navegador. */
export async function GET(_request: Request, { params }: { params: Promise<{ id: string }> }) {
  const sesion = await getSesion();
  if (!sesion) return NextResponse.json({ error: "no_autorizado" }, { status: 401 });
  const { id } = await params;
  if (!/^\d+$/.test(id)) return NextResponse.json({ error: "no_encontrado" }, { status: 404 });
  try {
    const respuesta = await llamarBackend<EvidenciaVista>(`/casos/${id}/evidencia`, {
      correlationId: randomUUID(), usuarioCi: sesion.usuario.cedula,
    });
    if (respuesta.status !== 200 || !respuesta.data) {
      return NextResponse.json({ error: "evidencia_no_disponible" }, {
        status: [403, 404].includes(respuesta.status) ? respuesta.status : 502,
        headers: { "Cache-Control": "private, no-store" },
      });
    }
    return NextResponse.json({
      eventosTracking: respuesta.data.eventosTracking ?? [],
      pruebasDesvio: respuesta.data.pruebasDesvio ?? [],
    }, { headers: { "Cache-Control": "private, no-store" } });
  } catch (error) {
    if (!(error instanceof BackendNoDisponible)) throw error;
    return NextResponse.json({ error: "servicio_no_disponible" }, {
      status: 503, headers: { "Cache-Control": "private, no-store" },
    });
  }
}

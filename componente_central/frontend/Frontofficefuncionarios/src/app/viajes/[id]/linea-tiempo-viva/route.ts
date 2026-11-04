import { randomUUID } from "node:crypto";
import { NextResponse } from "next/server";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import { idValido, type ViajeDetalle } from "@/lib/viajes";

/** Datos mínimos para refrescar la línea de tiempo; usa los permisos del servicio existente. */
export async function GET(_request: Request, { params }: { params: Promise<{ id: string }> }) {
  const sesion = await getSesion();
  if (!sesion) return NextResponse.json({ error: "no_autorizado" }, { status: 401 });
  const { id } = await params;
  if (!idValido(id)) return NextResponse.json({ error: "no_encontrado" }, { status: 404 });
  try {
    const res = await llamarBackend<ViajeDetalle>(`/viajes/${encodeURIComponent(id)}`, {
      correlationId: randomUUID(), usuarioCi: sesion.usuario.cedula,
    });
    if (res.status !== 200 || !res.data) {
      return NextResponse.json({ error: "viaje_no_disponible" }, {
        status: [403,404].includes(res.status) ? res.status : 502,
        headers: { "Cache-Control": "private, no-store" },
      });
    }
    return NextResponse.json({ estado: res.data.estado, eventos: res.data.eventos,
      modificaciones: res.data.modificaciones }, { headers: { "Cache-Control": "private, no-store" } });
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return NextResponse.json({ error: "servicio_no_disponible" }, { status: 503 });
  }
}

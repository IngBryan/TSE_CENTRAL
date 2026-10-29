import { randomUUID } from "node:crypto";
import { NextResponse, type NextRequest } from "next/server";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, descargarBackend } from "@/lib/backend";
import { idValido } from "@/lib/viajes";

// Foto adjunta a un evento del viaje (incidente). La pide el navegador con la cookie de sesion; el backend
// controla que quien la pide sea funcionario y que el evento sea de ese viaje.
export async function GET(_req: NextRequest, { params }: { params: Promise<{ id: string; idEvento: string }> }) {
  const sesion = await getSesion();
  if (!sesion) return NextResponse.json({ error: "no_autorizado" }, { status: 401 });
  const { id, idEvento } = await params;
  if (!idValido(id) || !idValido(idEvento)) return NextResponse.json({ error: "no_encontrada" }, { status: 404 });
  const correlationId = randomUUID();
  const ruta = `/viajes/${encodeURIComponent(id)}/eventos/${encodeURIComponent(idEvento)}/foto`;
  try {
    const res = await descargarBackend(ruta, { correlationId, usuarioCi: sesion.usuario.cedula });
    console.info(`[viajes] correlationId=${correlationId} GET ${ruta} status=${res.status}`);
    if (!res.ok) return NextResponse.json({ error: "no_encontrada" }, { status: res.status === 403 ? 403 : 404 });
    return new NextResponse(res.body, {
      headers: {
        "Content-Type": res.headers.get("content-type") ?? "image/jpeg",
        "Cache-Control": "private, max-age=3600",
      },
    });
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return NextResponse.json({ error: "no_disponible" }, { status: 503 });
  }
}

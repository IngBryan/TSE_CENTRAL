import { randomUUID } from "node:crypto";
import { NextResponse, type NextRequest } from "next/server";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, descargarBackend } from "@/lib/backend";

// Foto adjunta a un evento del viaje (incidente). El backend controla que la guia sea de la empresa
// del responsable de la sesion.
export async function GET(_req: NextRequest, { params }: { params: Promise<{ id: string; idEvento: string }> }) {
  const sesion = await getSesion();
  if (!sesion) return NextResponse.json({ error: "no_autorizado" }, { status: 401 });
  const { id, idEvento } = await params;
  try {
    const res = await descargarBackend(
      `/empresa/guias/${encodeURIComponent(id)}/eventos/${encodeURIComponent(idEvento)}/foto`,
      { correlationId: randomUUID(), usuarioCi: sesion.perfil.cedula },
    );
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

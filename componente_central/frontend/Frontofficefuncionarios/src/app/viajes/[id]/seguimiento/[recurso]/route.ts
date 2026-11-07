import { randomUUID } from "node:crypto";
import { NextResponse, type NextRequest } from "next/server";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import { idValido } from "@/lib/viajes";

/** BFF: la API key de WildFly nunca se envía al navegador. */
export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string; recurso: string }> },
) {
  const sesion = await getSesion();
  if (!sesion) return NextResponse.json({ error: "no_autorizado" }, { status: 401 });

  const { id, recurso } = await params;
  if (!idValido(id) || !["gps", "ultima", "rutas"].includes(recurso)) {
    return NextResponse.json({ error: "recurso_no_encontrado" }, { status: 404 });
  }

  let ruta = `/viajes/${encodeURIComponent(id)}`;
  if (recurso === "gps") {
    const desde = request.nextUrl.searchParams.get("desde");
    const hasta = request.nextUrl.searchParams.get("hasta");
    const limite = request.nextUrl.searchParams.get("limite") ?? "500";
    if (!desde || !hasta || !/^(?:[1-9]\d{0,2}|1000)$/.test(limite)) {
      return NextResponse.json({ error: "rango_invalido" }, { status: 400 });
    }
    ruta += `/gps?${new URLSearchParams({ desde, hasta, limite })}`;
  } else if (recurso === "ultima") {
    ruta += "/gps/ultima";
  } else {
    ruta += "/rutas-autorizadas";
  }

  const correlationId = randomUUID();
  try {
    const res = await llamarBackend<unknown>(ruta, {
      correlationId,
      usuarioCi: sesion.usuario.cedula,
    });
    if (res.status !== 200) {
      return NextResponse.json(
        { error: "consulta_seguimiento_fallida" },
        { status: [400, 403, 404].includes(res.status) ? res.status : 502 },
      );
    }
    return NextResponse.json(res.data, { headers: { "Cache-Control": "private, no-store" } });
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return NextResponse.json({ error: "seguimiento_no_disponible" }, { status: 503 });
  }
}

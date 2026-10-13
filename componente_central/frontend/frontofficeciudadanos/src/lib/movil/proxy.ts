import { NextResponse, type NextRequest } from "next/server";
import { BackendNoDisponible, llamarBackend } from "../backend";
import { autenticarMovil, correlationIdDe } from "./token";

// Rutas /api/movil/*: validan el Bearer de la app y llaman a la API movil del backend Java con la
// X-Api-Key, la cedula del token (X-Usuario-Ci) y un X-Correlation-Id. Devuelven el status y el
// cuerpo del backend tal cual (el movil interpreta 400/403/404/409 como rechazo definitivo); si el
// backend no responde, 503 y el movil reintenta.
export async function reenviarAlBackend(
  req: NextRequest,
  ruta: string,
  method: "GET" | "POST",
  body?: unknown,
): Promise<NextResponse> {
  const usuario = await autenticarMovil(req);
  if (usuario instanceof NextResponse) return usuario;
  const correlationId = correlationIdDe(req);
  const headers = { "X-Correlation-Id": correlationId, "Cache-Control": "no-store" };
  try {
    const res = await llamarBackend<unknown>(ruta, { method, body, correlationId, usuarioCi: usuario.cedula });
    console.info(`[movil] correlationId=${correlationId} ci=${usuario.cedula} ${method} ${ruta} status=${res.status}`);
    return NextResponse.json(res.data, { status: res.status, headers });
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    console.warn(`[movil] correlationId=${correlationId} ${method} ${ruta} backend no disponible: ${e.message}`);
    return NextResponse.json({ error: "no_disponible" }, { status: 503, headers });
  }
}

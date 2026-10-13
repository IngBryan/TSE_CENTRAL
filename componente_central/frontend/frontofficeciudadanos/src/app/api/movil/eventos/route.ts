import { NextResponse, type NextRequest } from "next/server";
import { reenviarAlBackend } from "@/lib/movil/proxy";

// Base64 de una foto de 5 MB (~6,7 MB) mas el resto del JSON
const MAX_BODY_BYTES = 7 * 1024 * 1024;

// Evento de viaje registrado en la app (idempotente por idEvento, ver MovilResource en el backend).
export async function POST(req: NextRequest) {
  if (Number(req.headers.get("content-length") ?? 0) > MAX_BODY_BYTES) {
    return NextResponse.json(
      { error: "datos_invalidos", campos: { foto: "La foto no puede superar los 5 MB." } },
      { status: 413 },
    );
  }
  const texto = await req.text();
  if (Buffer.byteLength(texto) > MAX_BODY_BYTES) {
    return NextResponse.json(
      { error: "datos_invalidos", campos: { foto: "La foto no puede superar los 5 MB." } },
      { status: 413 },
    );
  }
  let body: Record<string, unknown>;
  try {
    body = JSON.parse(texto);
  } catch {
    return NextResponse.json({ error: "datos_invalidos", campos: { body: "JSON inválido" } }, { status: 400 });
  }
  const { idEvento, idGuia, tipo, fechaHora, descripcion, latitud, longitud, foto, versionGuia } = body ?? {};
  return reenviarAlBackend(req, "/movil/eventos", "POST", {
    idEvento,
    idGuia,
    tipo,
    fechaHora,
    descripcion,
    latitud,
    longitud,
    foto,
    versionGuia,
  });
}

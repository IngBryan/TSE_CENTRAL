import { NextResponse, type NextRequest } from "next/server";
import { canjearCodigo } from "@/lib/movil/codigos";
import { emitirToken } from "@/lib/movil/token";

// La app movil canjea el codigo de un solo uso (que recibio en cargauy://auth?code=...) y su
// code_verifier por el token de 8 horas. Body: { "code": "...", "codeVerifier": "..." }.
export async function POST(req: NextRequest) {
  const body = (await req.json().catch(() => null)) as { code?: unknown; codeVerifier?: unknown } | null;
  const code = typeof body?.code === "string" ? body.code : null;
  const codeVerifier = typeof body?.codeVerifier === "string" ? body.codeVerifier : null;
  if (!code || !codeVerifier) {
    return NextResponse.json({ error: "solicitud_invalida" }, { status: 400 });
  }

  const perfil = canjearCodigo(code, codeVerifier);
  if (!perfil) {
    console.warn("[movil] canje de codigo rechazado (inexistente, vencido, usado o verifier incorrecto)");
    return NextResponse.json({ error: "codigo_invalido" }, { status: 400 });
  }

  const usuario = { cedula: perfil.cedula, nombre: perfil.nombre, roles: perfil.roles };
  const { token, expira } = await emitirToken(usuario);
  console.info(`[movil] token emitido ci=${perfil.cedula}`);
  return NextResponse.json(
    { token, tipo: "Bearer", expira, usuario },
    { headers: { "Cache-Control": "no-store" } },
  );
}

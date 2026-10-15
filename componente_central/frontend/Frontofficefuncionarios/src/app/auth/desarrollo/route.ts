import { NextResponse, type NextRequest } from "next/server";
import { getAuthConfig } from "@/lib/auth/config";
import { loginDesarrolloHabilitado } from "@/lib/auth/desarrollo";
import { COOKIE_SESION, DURACION_SESION_S, opcionesCookie, sellar, type Sesion } from "@/lib/auth/session";

// Login sin gub.uy, solo para desarrollo (ver lib/auth/desarrollo.ts). Si no esta habilitado, 404.
export async function POST(req: NextRequest) {
  if (!loginDesarrolloHabilitado()) {
    return new NextResponse(null, { status: 404 });
  }
  const cfg = getAuthConfig();
  const cedula = String((await req.formData()).get("cedula") ?? "").replace(/\D/g, "");
  if (!cedula) {
    return NextResponse.redirect(`${cfg.appBaseUrl}/?error=token_invalido`, 303);
  }
  console.warn(`[login desarrollo] ci=${cedula} sesion abierta sin gub.uy`);
  const sesion: Sesion = {
    usuario: { cedula, nombre: `C.I. ${cedula} (desarrollo)` },
    exp: Math.floor(Date.now() / 1000) + DURACION_SESION_S,
  };
  const res = NextResponse.redirect(`${cfg.appBaseUrl}/`, 303);
  res.cookies.set(COOKIE_SESION, sellar(sesion), opcionesCookie(DURACION_SESION_S));
  return res;
}

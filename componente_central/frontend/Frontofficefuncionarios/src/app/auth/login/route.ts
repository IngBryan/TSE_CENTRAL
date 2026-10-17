import { NextResponse, type NextRequest } from "next/server";
import { getAuthConfig, loginTestingHabilitado, mismoOrigen } from "@/lib/auth/config";
import { COOKIE_SESION, DURACION_SESION_S, opcionesCookie, sellar, type Sesion } from "@/lib/auth/session";

// Login de testing: abre sesion con la cedula ingresada, sin proveedor de identidad externo. Solo si
// LOGIN_TESTING=true (ver lib/auth/config.ts); si no, 404.
// El backend igual controla en cada operacion que la cedula sea de un funcionario registrado.
export async function POST(req: NextRequest) {
  if (!loginTestingHabilitado()) {
    return new NextResponse(null, { status: 404 });
  }
  if (!mismoOrigen(req)) {
    return new NextResponse(null, { status: 403 });
  }
  const cfg = getAuthConfig();
  const form = await req.formData().catch(() => null);
  const cedula = String(form?.get("cedula") ?? "").replace(/\D/g, "");
  if (!cedula) {
    return NextResponse.redirect(`${cfg.appBaseUrl}/?error=cedula_invalida`, 303);
  }
  console.info(`[login testing] ci=${cedula} sesion abierta`);
  const sesion: Sesion = {
    usuario: { cedula, nombre: `C.I. ${cedula}` },
    exp: Math.floor(Date.now() / 1000) + DURACION_SESION_S,
  };
  const res = NextResponse.redirect(`${cfg.appBaseUrl}/`, 303);
  res.cookies.set(COOKIE_SESION, sellar(sesion), opcionesCookie(DURACION_SESION_S));
  return res;
}

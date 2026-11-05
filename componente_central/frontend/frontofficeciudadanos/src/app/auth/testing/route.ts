import { randomUUID } from "node:crypto";
import { NextResponse, type NextRequest } from "next/server";
import { getAuthConfig, loginTestingHabilitado, mismoOrigen } from "@/lib/auth/config";
import { ErrorLogin } from "@/lib/auth/oidc";
import { ingresarParaTesting } from "@/lib/auth/perfil";
import { COOKIE_SESION, DURACION_SESION_S, opcionesCookie, sellar, type Sesion } from "@/lib/auth/session";
import { registrarLog } from "@/lib/log";

// Ingreso de testing: abre sesion con la cedula del formulario de la portada, sin gub.uy. Solo si
// LOGIN_TESTING=true (si no, 404). El backend hace el mismo ingreso que con gub.uy (perfil, PDI, roles),
// asi que se ve lo mismo que con el login real: responsable, chofer o ninguno.
export async function POST(req: NextRequest) {
  if (!loginTestingHabilitado()) {
    return new NextResponse(null, { status: 404 });
  }
  if (!mismoOrigen(req)) {
    return new NextResponse(null, { status: 403 });
  }
  const cfg = getAuthConfig();
  const volver = (motivo: string) => NextResponse.redirect(`${cfg.appBaseUrl}/?error=${motivo}`, 303);

  const form = await req.formData().catch(() => null);
  const cedula = String(form?.get("cedula") ?? "").replace(/\D/g, "");
  if (!/^\d{6,8}$/.test(cedula)) {
    return volver("cedula_invalida");
  }

  const correlationId = randomUUID();
  try {
    const { perfil, resultado, sesionPermitida } = await ingresarParaTesting(cedula, correlationId);
    registrarLog("info", "login.testing", { correlationId, usuario: cedula }, { resultado, estado: perfil.estado });
    if (!sesionPermitida) {
      return volver(resultado === "MENOR_DE_EDAD" ? "menor_de_edad" : "no_existe");
    }
    // Sin idToken: el logout cierra solo la sesion local (no hay sesion en gub.uy)
    const sesion: Sesion = { perfil, idToken: "", exp: Math.floor(Date.now() / 1000) + DURACION_SESION_S };
    const res = NextResponse.redirect(`${cfg.appBaseUrl}/mi-cuenta`, 303);
    res.cookies.set(COOKIE_SESION, sellar(sesion), opcionesCookie(DURACION_SESION_S));
    return res;
  } catch (e) {
    registrarLog("warn", "login.testing", { correlationId, usuario: cedula, error: (e as Error).message });
    return volver(e instanceof ErrorLogin ? e.motivo : "ingreso_no_disponible");
  }
}

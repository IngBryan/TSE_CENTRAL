import { randomUUID } from "node:crypto";
import { NextResponse, type NextRequest } from "next/server";
import { getAuthConfig } from "@/lib/auth/config";
import { canjearCodigo, ErrorLogin, obtenerUserinfo, validarIdToken, type MotivoError } from "@/lib/auth/oidc";
import {
  abrir,
  borrarCookie,
  COOKIE_SESION,
  COOKIE_TRANSACCION,
  DURACION_SESION_S,
  opcionesCookie,
  sellar,
  type Sesion,
  type TransaccionLogin,
} from "@/lib/auth/session";
import { cedulaDe, nombreDe } from "@/lib/auth/usuario";

// Vuelta desde ID Uruguay (OIDC_REDIRECT_URI). Aca solo se identifica a la persona: si es funcionario
// del MTOP lo controla el backend en cada operacion (capa de servicios).
export async function GET(req: NextRequest) {
  const cfg = getAuthConfig();
  const params = req.nextUrl.searchParams;
  const tx = abrir<TransaccionLogin>(req.cookies.get(COOKIE_TRANSACCION)?.value);
  const correlationId = randomUUID();

  const fallar = (motivo: MotivoError, detalle: string) => {
    console.warn(`[login gub.uy] correlationId=${correlationId} ${motivo}: ${detalle}`);
    const res = NextResponse.redirect(`${cfg.appBaseUrl}/?error=${motivo}`, 302);
    res.cookies.delete({ name: COOKIE_TRANSACCION, ...borrarCookie });
    return res;
  };

  const error = params.get("error");
  if (error) {
    // access_denied = cancelo o no dio el consentimiento
    const motivo: MotivoError = error === "access_denied" ? "cancelado" : "error_idp";
    return fallar(motivo, `${error} ${params.get("error_description") ?? ""}`);
  }

  const code = params.get("code");
  const state = params.get("state");
  if (!tx || !state || tx.state !== state || Date.now() - tx.creada > 10 * 60 * 1000) {
    return fallar("estado_invalido", "state ausente, vencido o distinto al enviado");
  }
  if (!code) {
    return fallar("token_invalido", "La respuesta no trae code");
  }

  try {
    const tokens = await canjearCodigo(code);
    const idToken = await validarIdToken(tokens.id_token, tx.nonce);
    const claims = await obtenerUserinfo(tokens.access_token, idToken.sub!);
    const cedula = cedulaDe(claims);
    if (!cedula) {
      return fallar("token_invalido", "gub.uy no devolvio la cedula (falta el scope document o personal_info)");
    }
    console.info(`[login gub.uy] correlationId=${correlationId} ci=${cedula} ingreso`);

    const sesion: Sesion = {
      usuario: { cedula, nombre: nombreDe(claims) },
      idToken: tokens.id_token,
      exp: Math.floor(Date.now() / 1000) + DURACION_SESION_S,
    };
    const res = NextResponse.redirect(`${cfg.appBaseUrl}/`, 302);
    res.cookies.delete({ name: COOKIE_TRANSACCION, ...borrarCookie });
    res.cookies.set(COOKIE_SESION, sellar(sesion), opcionesCookie(DURACION_SESION_S));
    return res;
  } catch (e) {
    if (e instanceof ErrorLogin) return fallar(e.motivo, e.message);
    return fallar("token_invalido", (e as Error).message);
  }
}

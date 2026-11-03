import { randomUUID } from "node:crypto";
import { NextResponse, type NextRequest } from "next/server";
import { getAuthConfig } from "@/lib/auth/config";
import { ingresarConGubUy } from "@/lib/auth/ingreso";
import { canjearCodigo, ErrorLogin, obtenerUserinfo, validarIdToken, type MotivoError } from "@/lib/auth/oidc";
import {
  abrir,
  borrarCookie,
  COOKIE_SESION,
  COOKIE_TRANSACCION,
  DURACION_SESION_S,
  DURACION_TRANSACCION_S,
  opcionesCookie,
  sellar,
  type Sesion,
  type TransaccionLogin,
} from "@/lib/auth/session";
import { registrarLog } from "@/lib/log";

// Vuelta desde ID Uruguay. La redirect URI registrada es la raiz de la app (appBaseUrl, p. ej.
// https://env-6537388.web.elasticloud.uy/funcionarios), asi que page.tsx reenvia aca los
// ?code=...&state=... (o ?error=...).
export async function GET(req: NextRequest) {
  const cfg = getAuthConfig();
  const params = req.nextUrl.searchParams;
  const tx = abrir<TransaccionLogin>(req.cookies.get(COOKIE_TRANSACCION)?.value);
  // Un correlation id por ingreso: va en los logs del front, del backend y de la PDI
  const correlationId = randomUUID();

  const fallar = (motivo: MotivoError, detalle: string) => {
    registrarLog("warn", "login.gubuy", { correlationId }, { detalle: `${motivo}: ${detalle}` });
    const res = NextResponse.redirect(`${cfg.appBaseUrl}/?error=${motivo}`, 302);
    res.cookies.delete({ name: COOKIE_TRANSACCION, ...borrarCookie });
    return res;
  };

  const error = params.get("error");
  if (error) {
    // access_denied = el funcionario cancelo o no dio el consentimiento
    const motivo: MotivoError = error === "access_denied" ? "cancelado" : "error_idp";
    return fallar(motivo, `${error} ${params.get("error_description") ?? ""}`);
  }

  const code = params.get("code");
  const state = params.get("state");
  if (!tx || !state || tx.state !== state || Date.now() - tx.creada > DURACION_TRANSACCION_S * 1000) {
    return fallar("estado_invalido", "state ausente, vencido o distinto al enviado");
  }
  if (!code) {
    return fallar("token_invalido", "La respuesta no trae code");
  }

  try {
    const tokens = await canjearCodigo(code);
    const idToken = await validarIdToken(tokens.id_token, tx.nonce);
    const claims = await obtenerUserinfo(tokens.access_token, idToken.sub!);
    // Funcionario registrado + validacion contra la PDI (ErrorLogin si no se puede entrar)
    const { usuario, resultado } = await ingresarConGubUy(claims, correlationId);
    registrarLog("info", "login.gubuy", { correlationId }, { detalle: `ci=${usuario.cedula} resultado=${resultado}` });

    const sesion: Sesion = {
      usuario,
      idToken: tokens.id_token,
      exp: Math.floor(Date.now() / 1000) + DURACION_SESION_S,
    };
    const res = NextResponse.redirect(`${cfg.appBaseUrl}/casos`, 302);
    res.cookies.delete({ name: COOKIE_TRANSACCION, ...borrarCookie });
    res.cookies.set(COOKIE_SESION, sellar(sesion), opcionesCookie(DURACION_SESION_S));
    return res;
  } catch (e) {
    if (e instanceof ErrorLogin) return fallar(e.motivo, e.message);
    return fallar("token_invalido", (e as Error).message);
  }
}

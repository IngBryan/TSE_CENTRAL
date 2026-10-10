import { NextResponse, type NextRequest } from "next/server";
import { getAuthConfig } from "@/lib/auth/config";
import { canjearCodigo, ErrorLogin, obtenerUserinfo, validarIdToken, type MotivoError } from "@/lib/auth/oidc";
import { buscarOCrearPerfil } from "@/lib/auth/perfil";
import {
  abrir,
  COOKIE_SESION,
  COOKIE_TRANSACCION,
  DURACION_SESION_S,
  opcionesCookie,
  sellar,
  type Sesion,
  type TransaccionLogin,
} from "@/lib/auth/session";

// Vuelta desde ID Uruguay. La redirect URI registrada es la raiz (http://localhost), asi que
// page.tsx reenvia aca los ?code=...&state=... (o ?error=...).
export async function GET(req: NextRequest) {
  const cfg = getAuthConfig();
  const params = req.nextUrl.searchParams;
  const tx = abrir<TransaccionLogin>(req.cookies.get(COOKIE_TRANSACCION)?.value);

  const fallar = (motivo: MotivoError, detalle: string) => {
    console.warn(`[login gub.uy] ${motivo}: ${detalle}`);
    const res = NextResponse.redirect(`${cfg.appBaseUrl}/?error=${motivo}`, 302);
    res.cookies.delete(COOKIE_TRANSACCION);
    return res;
  };

  const error = params.get("error");
  if (error) {
    // access_denied = el ciudadano cancelo o no dio el consentimiento
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
    const { perfil, nuevo } = await buscarOCrearPerfil(claims);

    const sesion: Sesion = {
      perfil,
      idToken: tokens.id_token,
      exp: Math.floor(Date.now() / 1000) + DURACION_SESION_S,
    };
    console.info(`[login gub.uy] ingreso ci=${perfil.cedula} nuevo=${nuevo}`);

    const res = NextResponse.redirect(`${cfg.appBaseUrl}/mi-cuenta${nuevo ? "?bienvenida=1" : ""}`, 302);
    res.cookies.delete(COOKIE_TRANSACCION);
    res.cookies.set(COOKIE_SESION, sellar(sesion), opcionesCookie(DURACION_SESION_S));
    return res;
  } catch (e) {
    if (e instanceof ErrorLogin) return fallar(e.motivo, e.message);
    return fallar("token_invalido", (e as Error).message);
  }
}

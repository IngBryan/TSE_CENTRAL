import { randomUUID } from "node:crypto";
import { NextResponse, type NextRequest } from "next/server";
import { getAuthConfig } from "@/lib/auth/config";
import { canjearCodigo, ErrorLogin, obtenerUserinfo, validarIdToken, type MotivoError } from "@/lib/auth/oidc";
import { ingresarEnBackend } from "@/lib/auth/perfil";
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
import { emitirCodigo } from "@/lib/movil/codigos";
import { volverAlMovil } from "@/lib/movil/volver";

// Vuelta desde ID Uruguay. La redirect URI registrada es la raiz (http://localhost), asi que
// page.tsx reenvia aca los ?code=...&state=... (o ?error=...).
export async function GET(req: NextRequest) {
  const cfg = getAuthConfig();
  const params = req.nextUrl.searchParams;
  const tx = abrir<TransaccionLogin>(req.cookies.get(COOKIE_TRANSACCION)?.value);
  // Un correlation id por ingreso: va en los logs del front, del backend y de la PDI
  const correlationId = randomUUID();

  const fallar = (motivo: MotivoError | "no_chofer", detalle: string) => {
    console.warn(`[login gub.uy] correlationId=${correlationId} ${motivo}: ${detalle}`);
    if (tx?.movil) return volverAlMovil(tx.movil.redirect, { error: motivo });
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
    const { perfil, resultado, sesionPermitida, nuevo } = await ingresarEnBackend(claims, correlationId);
    console.info(
      `[login gub.uy] correlationId=${correlationId} ci=${perfil.cedula} nuevo=${nuevo} resultado=${resultado} estado=${perfil.estado}`,
    );

    // CU-01 FA6 / FA7: la PDI dice que no existe o que es menor de edad, no se inicia sesion
    if (!sesionPermitida) {
      return fallar(resultado === "MENOR_DE_EDAD" ? "menor_de_edad" : "no_existe", `resultado ${resultado}`);
    }

    // Login de la app movil: solo choferes habilitados. No se crea la sesion web: se vuelve a la app
    // con un codigo de un solo uso que la app canjea por su token en POST /api/movil/token.
    if (tx.movil) {
      if (!perfil.roles.some((r) => r.rol === "CHOFER" && r.estado === "HABILITADO")) {
        return fallar("no_chofer", `ci=${perfil.cedula} no es chofer habilitado`);
      }
      console.info(`[login gub.uy] correlationId=${correlationId} ci=${perfil.cedula} flujo=movil codigo emitido`);
      return volverAlMovil(tx.movil.redirect, { code: emitirCodigo(perfil, tx.movil.codeChallenge) });
    }

    // FA8 (NO_DISPONIBLE): sesion con el perfil pendiente de validacion
    const sesion: Sesion = {
      perfil,
      idToken: tokens.id_token,
      exp: Math.floor(Date.now() / 1000) + DURACION_SESION_S,
    };

    const res = NextResponse.redirect(`${cfg.appBaseUrl}/mi-cuenta${nuevo ? "?bienvenida=1" : ""}`, 302);
    res.cookies.delete(COOKIE_TRANSACCION);
    res.cookies.set(COOKIE_SESION, sellar(sesion), opcionesCookie(DURACION_SESION_S));
    return res;
  } catch (e) {
    if (e instanceof ErrorLogin) return fallar(e.motivo, e.message);
    return fallar("token_invalido", (e as Error).message);
  }
}

import { NextResponse, type NextRequest } from "next/server";
import { urlAutorizacion, valorAleatorio } from "@/lib/auth/oidc";
import {
  COOKIE_TRANSACCION,
  DURACION_TRANSACCION_S,
  opcionesCookie,
  sellar,
  type TransaccionLogin,
} from "@/lib/auth/session";
import { challengeValido, REDIRECTS_MOVIL } from "@/lib/movil/codigos";

// Login de la app movil: la app abre esta URL en un Custom Tab con
//   ?redirect=cargauy://auth&code_challenge=<S256>&code_challenge_method=S256
// Arranca el mismo login con gub.uy que la web (misma redirect URI http://localhost), marcando en la
// cookie de la transaccion que el flujo es movil. El callback, en vez de crear la sesion web, emite un
// codigo de un solo uso y vuelve a la app (ver /auth/callback).
export async function GET(req: NextRequest) {
  const params = req.nextUrl.searchParams;
  const redirect = params.get("redirect") ?? REDIRECTS_MOVIL[0];
  const codeChallenge = params.get("code_challenge");
  if (!REDIRECTS_MOVIL.includes(redirect)) {
    return NextResponse.json({ error: "redirect_invalido" }, { status: 400 });
  }
  if (!challengeValido(codeChallenge) || (params.get("code_challenge_method") ?? "S256") !== "S256") {
    return NextResponse.json({ error: "code_challenge_invalido" }, { status: 400 });
  }

  const tx: TransaccionLogin = {
    state: valorAleatorio(),
    nonce: valorAleatorio(),
    creada: Date.now(),
    movil: { redirect, codeChallenge },
  };
  const res = NextResponse.redirect(urlAutorizacion(tx.state, tx.nonce), 302);
  res.cookies.set(COOKIE_TRANSACCION, sellar(tx), opcionesCookie(DURACION_TRANSACCION_S));
  return res;
}

import { NextResponse } from "next/server";
import { urlAutorizacion, valorAleatorio } from "@/lib/auth/oidc";
import {
  COOKIE_TRANSACCION,
  DURACION_TRANSACCION_S,
  opcionesCookie,
  sellar,
  type TransaccionLogin,
} from "@/lib/auth/session";

// Boton "Ingresar con gub.uy": genera state y nonce, los guarda en una cookie cifrada y
// redirige (302) al Authorization Endpoint de ID Uruguay.
export async function GET() {
  const tx: TransaccionLogin = { state: valorAleatorio(), nonce: valorAleatorio(), creada: Date.now() };
  const res = NextResponse.redirect(urlAutorizacion(tx.state, tx.nonce), 302);
  res.cookies.set(COOKIE_TRANSACCION, sellar(tx), opcionesCookie(DURACION_TRANSACCION_S));
  return res;
}

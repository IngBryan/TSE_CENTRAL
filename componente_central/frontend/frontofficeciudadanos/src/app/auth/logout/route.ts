import { NextResponse, type NextRequest } from "next/server";
import { getAuthConfig } from "@/lib/auth/config";
import { urlLogout, valorAleatorio } from "@/lib/auth/oidc";
import { abrir, COOKIE_SESION, type Sesion } from "@/lib/auth/session";

// Boton "Cerrar sesion": borra la sesion local y cierra tambien la sesion en ID Uruguay.
// gub.uy vuelve a OIDC_POST_LOGOUT_REDIRECT_URI (http://localhost/logout).
export async function GET(req: NextRequest) {
  const sesion = abrir<Sesion>(req.cookies.get(COOKIE_SESION)?.value);
  const destino = sesion?.idToken
    ? urlLogout(sesion.idToken, valorAleatorio())
    : `${getAuthConfig().appBaseUrl}/logout`;

  const res = NextResponse.redirect(destino, 302);
  res.cookies.delete(COOKIE_SESION);
  return res;
}

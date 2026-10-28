import { NextResponse, type NextRequest } from "next/server";
import { getAuthConfig, mismoOrigen } from "@/lib/auth/config";
import { urlLogout, valorAleatorio } from "@/lib/auth/oidc";
import { abrir, borrarCookie, COOKIE_SESION, type Sesion } from "@/lib/auth/session";

// Boton "Cerrar sesion": borra la sesion local y, si se entro con gub.uy, cierra tambien la sesion en
// ID Uruguay, que vuelve a appBaseUrl/logout (src/app/logout/route.ts). POST para que otro sitio no pueda
// cerrar la sesion con un link o una imagen.
export async function POST(req: NextRequest) {
  if (!mismoOrigen(req)) {
    return new NextResponse(null, { status: 403 });
  }
  const sesion = abrir<Sesion>(req.cookies.get(COOKIE_SESION)?.value);
  const destino = sesion?.idToken
    ? urlLogout(sesion.idToken, valorAleatorio())
    : `${getAuthConfig().appBaseUrl}/?sesion=cerrada`;

  const res = NextResponse.redirect(destino, 303);
  res.cookies.delete({ name: COOKIE_SESION, ...borrarCookie });
  return res;
}

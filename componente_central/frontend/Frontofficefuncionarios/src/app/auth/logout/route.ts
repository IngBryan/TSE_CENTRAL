import { NextResponse, type NextRequest } from "next/server";
import { getAuthConfig, mismoOrigen } from "@/lib/auth/config";
import { borrarCookie, COOKIE_SESION } from "@/lib/auth/session";

// Boton "Cerrar sesion": borra la sesion local y vuelve al inicio. POST para que otro sitio no pueda
// cerrar la sesion con un link o una imagen.
export async function POST(req: NextRequest) {
  if (!mismoOrigen(req)) {
    return new NextResponse(null, { status: 403 });
  }
  const res = NextResponse.redirect(`${getAuthConfig().appBaseUrl}/?sesion=cerrada`, 303);
  res.cookies.delete({ name: COOKIE_SESION, ...borrarCookie });
  return res;
}

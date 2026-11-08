import { NextResponse, type NextRequest } from "next/server";

import { inicioPorRol, rolesPermitidos, RUTA_CERRAR_SESION } from "@/lib/rutas";
import { leerToken, NOMBRE_COOKIE_SESION, RUTA_COOKIE_SESION } from "@/lib/token";

export function proxy(request: NextRequest) {
  // nextUrl.pathname comes without the basePath (/backoffice)
  const ruta = request.nextUrl.pathname;
  // requerirRol sends here a token that decodes but WildFly rejects: Server Components cannot delete
  // cookies, and without dropping it /login and the page would keep redirecting to each other
  if (ruta === RUTA_CERRAR_SESION) {
    return cerrarSesion(request);
  }

  const sesion = leerToken(request.cookies.get(NOMBRE_COOKIE_SESION)?.value);

  if (ruta === "/" || ruta === "/login") {
    return sesion ? redirigir(request, inicioPorRol(sesion.rol)) : NextResponse.next();
  }

  const roles = rolesPermitidos(ruta);

  if (!roles) {
    return NextResponse.next();
  }

  // An expired or malformed token is useless: drop it so the login page starts clean
  if (!sesion) {
    return cerrarSesion(request);
  }

  if (!roles.includes(sesion.rol)) {
    return redirigir(request, inicioPorRol(sesion.rol));
  }

  return NextResponse.next();
}

function cerrarSesion(request: NextRequest) {
  const respuesta = redirigir(request, "/login");
  if (request.cookies.has(NOMBRE_COOKIE_SESION)) {
    respuesta.cookies.delete({ name: NOMBRE_COOKIE_SESION, path: RUTA_COOKIE_SESION });
  }
  return respuesta;
}

function redirigir(request: NextRequest, ruta: string) {
  const url = request.nextUrl.clone();
  url.pathname = ruta;
  url.search = "";
  return NextResponse.redirect(url);
}

export const config = {
  // With the basePath the second pattern does not match /backoffice itself, hence the explicit "/"
  matcher: ["/", "/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp|ico)$).*)"],
};

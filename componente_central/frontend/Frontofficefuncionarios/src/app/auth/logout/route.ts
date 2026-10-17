import { NextResponse } from "next/server";
import { getAuthConfig } from "@/lib/auth/config";
import { borrarCookie, COOKIE_SESION } from "@/lib/auth/session";

// Boton "Cerrar sesion": borra la sesion local y vuelve al inicio.
export async function GET() {
  const res = NextResponse.redirect(`${getAuthConfig().appBaseUrl}/?sesion=cerrada`, 302);
  res.cookies.delete({ name: COOKIE_SESION, ...borrarCookie });
  return res;
}

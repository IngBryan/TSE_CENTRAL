import { NextResponse } from "next/server";
import { getAuthConfig } from "@/lib/auth/config";
import { borrarCookie, COOKIE_SESION } from "@/lib/auth/session";

// post_logout_redirect_uri: vuelta desde ID Uruguay despues de cerrar la sesion alla.
export async function GET() {
  const res = NextResponse.redirect(`${getAuthConfig().appBaseUrl}/?sesion=cerrada`, 302);
  res.cookies.delete({ name: COOKIE_SESION, ...borrarCookie });
  return res;
}

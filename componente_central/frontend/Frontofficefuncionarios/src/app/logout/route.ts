import { NextResponse } from "next/server";
import { getAuthConfig } from "@/lib/auth/config";
import { borrarCookie, COOKIE_SESION } from "@/lib/auth/session";

// post_logout_redirect_uri registrada en ID Uruguay (appBaseUrl/logout, p. ej.
// https://env-6537388.web.elasticloud.uy/funcionarios/logout).
export async function GET() {
  const res = NextResponse.redirect(`${getAuthConfig().appBaseUrl}/?sesion=cerrada`, 302);
  res.cookies.delete({ name: COOKIE_SESION, ...borrarCookie });
  return res;
}

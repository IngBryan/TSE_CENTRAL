import { NextResponse } from "next/server";
import { getAuthConfig } from "@/lib/auth/config";
import { COOKIE_SESION } from "@/lib/auth/session";

// post_logout_redirect_uri registrada en ID Uruguay (http://localhost/logout).
export async function GET() {
  const res = NextResponse.redirect(`${getAuthConfig().appBaseUrl}/?sesion=cerrada`, 302);
  res.cookies.delete(COOKIE_SESION);
  return res;
}

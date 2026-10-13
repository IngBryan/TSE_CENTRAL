import { NextResponse } from "next/server";
import { COOKIE_TRANSACCION } from "../auth/session";

// Vuelta del login movil a la app (cargauy://auth?code=... o ?error=...). Se responde una pagina en
// vez de un 302: Chrome puede bloquear la apertura automatica de un esquema propio sin un toque del
// usuario, y en ese caso queda el boton "Volver a la app". El code no sirve sin el code_verifier
// que solo tiene la app (PKCE) y vence en 60 segundos; el token nunca va en la URL.
export function volverAlMovil(redirect: string, params: Record<string, string>): NextResponse {
  const destino = `${redirect}?${new URLSearchParams(params)}`;
  const ok = "code" in params;
  const html = `<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>carga.uy</title>
<style>body{font-family:system-ui,sans-serif;display:flex;min-height:90vh;align-items:center;justify-content:center;text-align:center;padding:16px}
a{display:inline-block;margin-top:16px;padding:12px 24px;border-radius:999px;background:#0b5394;color:#fff;text-decoration:none}</style>
</head><body><main>
<p>${ok ? "Listo, ya podés volver a la app carga.uy." : "No se pudo completar el ingreso."}</p>
<a id="volver" href="${escaparHtml(destino)}">Volver a la app</a>
</main><script>location.replace(document.getElementById("volver").href)</script></body></html>`;
  const res = new NextResponse(html, {
    status: 200,
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "no-store",
      "Referrer-Policy": "no-referrer",
    },
  });
  res.cookies.delete(COOKIE_TRANSACCION);
  return res;
}

function escaparHtml(s: string) {
  return s.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

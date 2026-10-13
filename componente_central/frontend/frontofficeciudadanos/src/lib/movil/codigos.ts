import { createHash, randomBytes, timingSafeEqual } from "node:crypto";
import type { PerfilCiudadano } from "../auth/perfil";

// Codigos de un solo uso del login de la app movil. Despues del login con gub.uy el servidor de Next
// redirige a cargauy://auth?code=...; la app canjea ese code (con su code_verifier, PKCE S256) en
// POST /api/movil/token. El code vive 60 segundos y se borra al primer intento de canje.
//
// Se guardan en memoria del proceso de Next (globalThis, para que todas las rutas compartan el
// mismo Map): alcanza con una sola instancia del front. Con varias habria que llevarlo a una base.

export const REDIRECTS_MOVIL = ["cargauy://auth"];
const DURACION_CODIGO_MS = 60_000;
const CHALLENGE = /^[A-Za-z0-9_-]{43}$/; // base64url de un SHA-256

type CodigoMovil = { perfil: PerfilCiudadano; codeChallenge: string; vence: number };

const g = globalThis as { __codigosMovil?: Map<string, CodigoMovil> };
const codigos = (g.__codigosMovil ??= new Map());

export function challengeValido(challenge: string | null): challenge is string {
  return !!challenge && CHALLENGE.test(challenge);
}

export function emitirCodigo(perfil: PerfilCiudadano, codeChallenge: string): string {
  const ahora = Date.now();
  for (const [code, c] of codigos) if (c.vence < ahora) codigos.delete(code);
  const code = randomBytes(32).toString("base64url");
  codigos.set(code, { perfil, codeChallenge, vence: ahora + DURACION_CODIGO_MS });
  return code;
}

// Devuelve el perfil si el code existe, no vencio y el verifier corresponde al challenge. El code
// se consume siempre (aunque el verifier este mal) para que no se pueda probar varias veces.
export function canjearCodigo(code: string, codeVerifier: string): PerfilCiudadano | null {
  const c = codigos.get(code);
  codigos.delete(code);
  if (!c || c.vence < Date.now()) return null;
  const calculado = Buffer.from(createHash("sha256").update(codeVerifier).digest("base64url"));
  const esperado = Buffer.from(c.codeChallenge);
  return calculado.length === esperado.length && timingSafeEqual(calculado, esperado) ? c.perfil : null;
}

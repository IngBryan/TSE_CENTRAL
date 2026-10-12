import { createCipheriv, createDecipheriv, createHash, randomBytes } from "node:crypto";
import { cookies } from "next/headers";
import { getAuthConfig } from "./config";
import type { PerfilCiudadano } from "./perfil";

// La sesion y la transaccion de login viajan en cookies httpOnly cifradas con AES-256-GCM
// (clave derivada de SESSION_SECRET): el navegador no puede leerlas ni modificarlas.

export const COOKIE_SESION = "ciudadanos_session";
export const COOKIE_TRANSACCION = "gubuy_tx";
export const DURACION_SESION_S = 60 * 60 * 8;
export const DURACION_TRANSACCION_S = 60 * 10; // el code de ID Uruguay vence a los 10 minutos

export type Sesion = {
  perfil: PerfilCiudadano;
  idToken: string; // se guarda para mandarlo como id_token_hint en el logout
  exp: number; // epoch en segundos
};

export type TransaccionLogin = {
  state: string;
  nonce: string;
  creada: number;
  // Solo en el login de la app movil (/auth/movil/login): a donde volver y el challenge PKCE de la app
  movil?: { redirect: string; codeChallenge: string };
};

function clave(): Buffer {
  return createHash("sha256").update(getAuthConfig().sessionSecret).digest();
}

export function sellar(datos: unknown): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", clave(), iv);
  const cifrado = Buffer.concat([cipher.update(JSON.stringify(datos), "utf8"), cipher.final()]);
  return Buffer.concat([iv, cipher.getAuthTag(), cifrado]).toString("base64url");
}

export function abrir<T>(valor: string | undefined): T | null {
  if (!valor) return null;
  try {
    const buf = Buffer.from(valor, "base64url");
    const decipher = createDecipheriv("aes-256-gcm", clave(), buf.subarray(0, 12));
    decipher.setAuthTag(buf.subarray(12, 28));
    const plano = Buffer.concat([decipher.update(buf.subarray(28)), decipher.final()]);
    return JSON.parse(plano.toString("utf8")) as T;
  } catch {
    return null; // cookie adulterada o firmada con otro secreto
  }
}

export function opcionesCookie(maxAge: number) {
  return {
    httpOnly: true,
    secure: getAuthConfig().cookiesSeguras,
    // lax: la cookie tiene que viajar en el redirect top-level que vuelve desde gub.uy
    sameSite: "lax" as const,
    path: "/",
    maxAge,
  };
}

// Para Server Components: devuelve la sesion vigente o null.
export async function getSesion(): Promise<Sesion | null> {
  const sesion = abrir<Sesion>((await cookies()).get(COOKIE_SESION)?.value);
  if (!sesion || sesion.exp < Date.now() / 1000) return null;
  return sesion;
}

import { createCipheriv, createDecipheriv, createHash, randomBytes } from "node:crypto";
import { cookies } from "next/headers";
import { getAuthConfig } from "./config";

// La sesion viaja en una cookie httpOnly cifrada con AES-256-GCM (clave derivada de
// SESSION_SECRET): el navegador no puede leerla ni modificarla.
// La cookie va con path /funcionarios para no mezclarse con las de frontofficeciudadanos si
// ambos quedan publicados en el mismo host.

export const COOKIE_SESION = "funcionarios_session";
export const DURACION_SESION_S = 60 * 60 * 8;
const PATH_COOKIES = "/funcionarios";

// Quien inicio sesion. El front no decide si es funcionario: lo controla el backend en cada
// operacion (403 si la cedula no esta registrada como funcionario del MTOP).
export type Usuario = { cedula: string; nombre: string };

export type Sesion = {
  usuario: Usuario;
  exp: number; // epoch en segundos
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
    sameSite: "lax" as const,
    path: PATH_COOKIES,
    maxAge,
  };
}

export const borrarCookie = { path: PATH_COOKIES };

// Para Server Components y Server Actions: devuelve la sesion vigente o null.
export async function getSesion(): Promise<Sesion | null> {
  const sesion = abrir<Sesion>((await cookies()).get(COOKIE_SESION)?.value);
  if (!sesion || sesion.exp < Date.now() / 1000) return null;
  return sesion;
}

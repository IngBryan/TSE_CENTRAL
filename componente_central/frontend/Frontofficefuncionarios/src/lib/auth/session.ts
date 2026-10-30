import { createCipheriv, createDecipheriv, createHash, randomBytes } from "node:crypto";
import { cookies } from "next/headers";
import { getAuthConfig } from "./config";

// La sesion viaja en una cookie httpOnly cifrada con AES-256-GCM (clave derivada de
// SESSION_SECRET): el navegador no puede leerla ni modificarla.
// La cookie va con path /funcionarios para no mezclarse con las de frontofficeciudadanos si
// ambos quedan publicados en el mismo host.

export const COOKIE_SESION = "funcionarios_session";
export const COOKIE_TRANSACCION = "funcionarios_gubuy_tx";
export const DURACION_SESION_S = 60 * 60 * 8;
export const DURACION_TRANSACCION_S = 60 * 10; // el code de ID Uruguay vence a los 10 minutos
const PATH_COOKIES = "/funcionarios";

// Quien inicio sesion. El backend controlo al ingresar que sea funcionario registrado (y lo valido contra
// la PDI), y lo vuelve a controlar en cada operacion (403 si la cedula no es de un funcionario del MTOP).
// identidadVerificada: false si la PDI no respondio al ingresar (se entro igual, en modo degradado). Las
// sesiones abiertas antes de agregar el campo no lo traen y se toman como verificadas.
export type Usuario = { cedula: string; nombre: string; email?: string; identidadVerificada?: boolean };

export type Sesion = {
  usuario: Usuario;
  idToken: string; // id_token_hint del logout en gub.uy; vacio en el ingreso de testing
  exp: number; // epoch en segundos
};

export type TransaccionLogin = {
  state: string;
  nonce: string;
  creada: number;
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

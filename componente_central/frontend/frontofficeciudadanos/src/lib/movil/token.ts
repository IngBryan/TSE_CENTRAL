import { randomUUID } from "node:crypto";
import { errors, jwtVerify, SignJWT } from "jose";
import { NextResponse, type NextRequest } from "next/server";
import type { Rol } from "../auth/perfil";

// Token de la app movil: JWT HS256 firmado con MOVIL_TOKEN_SECRET (solo lo conoce el servidor de
// Next). Lleva la cedula (sub), el nombre y los roles, y vence a las 8 horas: despues el chofer
// vuelve a ingresar con gub.uy. El backend Java no lo ve: Next lo valida y le pasa la cedula en
// X-Usuario-Ci junto con la X-Api-Key.

export const DURACION_TOKEN_S = 60 * 60 * 8;
const EMISOR = "carga.uy";
const AUDIENCIA = "carga.uy-movil";

export type UsuarioMovil = { cedula: string; nombre: string; roles: Rol[] };

function secreto(): Uint8Array {
  const valor = process.env.MOVIL_TOKEN_SECRET;
  if (!valor || valor.length < 32) {
    throw new Error("Falta MOVIL_TOKEN_SECRET (al menos 32 caracteres, ver .env.example)");
  }
  return new TextEncoder().encode(valor);
}

export async function emitirToken(usuario: UsuarioMovil): Promise<{ token: string; expira: number }> {
  const expira = Math.floor(Date.now() / 1000) + DURACION_TOKEN_S;
  const token = await new SignJWT({ nombre: usuario.nombre, roles: usuario.roles })
    .setProtectedHeader({ alg: "HS256", typ: "JWT" })
    .setSubject(usuario.cedula)
    .setIssuer(EMISOR)
    .setAudience(AUDIENCIA)
    .setIssuedAt()
    .setExpirationTime(expira)
    .setJti(randomUUID())
    .sign(secreto());
  return { token, expira };
}

// Valida el header Authorization: Bearer <token>. Devuelve el usuario o una respuesta 401
// ({ error: "token_vencido" } o { error: "token_invalido" }).
export async function autenticarMovil(req: NextRequest): Promise<UsuarioMovil | NextResponse> {
  const auth = req.headers.get("authorization");
  const token = auth?.match(/^Bearer\s+(\S+)$/i)?.[1];
  if (!token) return noAutorizado("token_invalido");
  try {
    const { payload } = await jwtVerify(token, secreto(), {
      algorithms: ["HS256"],
      issuer: EMISOR,
      audience: AUDIENCIA,
    });
    if (!payload.sub) return noAutorizado("token_invalido");
    return {
      cedula: payload.sub,
      nombre: String(payload.nombre ?? ""),
      roles: (payload.roles as Rol[] | undefined) ?? [],
    };
  } catch (e) {
    return noAutorizado(e instanceof errors.JWTExpired ? "token_vencido" : "token_invalido");
  }
}

function noAutorizado(error: "token_vencido" | "token_invalido") {
  return NextResponse.json({ error }, { status: 401, headers: { "WWW-Authenticate": `Bearer error="invalid_token"` } });
}

const ID_VALIDO = /^[A-Za-z0-9._-]{1,64}$/;

// El movil puede mandar su propio X-Correlation-Id (por ejemplo el idEvento) para seguirlo en los logs.
export function correlationIdDe(req: NextRequest): string {
  const recibido = req.headers.get("x-correlation-id");
  return recibido && ID_VALIDO.test(recibido) ? recibido : randomUUID();
}

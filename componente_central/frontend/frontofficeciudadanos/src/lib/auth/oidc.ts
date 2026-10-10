import { createPublicKey, randomBytes, verify, type JsonWebKey as NodeJsonWebKey } from "node:crypto";
import { decodeJwt, decodeProtectedHeader, jwtVerify, type JWTPayload } from "jose";
import { getAuthConfig } from "./config";

// Cliente OIDC de ID Uruguay (Authorization Code Flow). Todo corre en el servidor de Next:
// el navegador nunca ve el client secret ni los tokens.

export type MotivoError =
  | "cancelado" // el ciudadano cancelo o rechazo el consentimiento en gub.uy
  | "estado_invalido" // state ausente, vencido o distinto (posible CSRF)
  | "token_invalido" // code rechazado, firma/iss/aud/nonce del id_token invalidos, sub distinto
  | "idp_no_disponible" // gub.uy no responde, timeout o error 5xx
  | "error_idp"; // otro error informado por gub.uy

export class ErrorLogin extends Error {
  constructor(
    public motivo: MotivoError,
    detalle: string,
  ) {
    super(detalle);
  }
}

export function valorAleatorio(): string {
  return randomBytes(32).toString("base64url");
}

export function urlAutorizacion(state: string, nonce: string): string {
  const cfg = getAuthConfig();
  const url = new URL(cfg.authorizationEndpoint);
  url.search = new URLSearchParams({
    response_type: "code",
    scope: cfg.scopes,
    client_id: cfg.clientId,
    redirect_uri: cfg.redirectUri,
    state,
    nonce,
  }).toString();
  return url.toString();
}

export function urlLogout(idToken: string, state: string): string {
  const cfg = getAuthConfig();
  const url = new URL(cfg.endSessionEndpoint);
  url.search = new URLSearchParams({
    id_token_hint: idToken,
    post_logout_redirect_uri: cfg.postLogoutRedirectUri,
    state,
  }).toString();
  return url.toString();
}

async function llamarIdp(url: string, init: RequestInit): Promise<Response> {
  try {
    const res = await fetch(url, { ...init, cache: "no-store", signal: AbortSignal.timeout(getAuthConfig().timeoutMs) });
    if (res.status >= 500) {
      throw new ErrorLogin("idp_no_disponible", `${url} respondio ${res.status}`);
    }
    return res;
  } catch (e) {
    if (e instanceof ErrorLogin) throw e;
    throw new ErrorLogin("idp_no_disponible", `No se pudo contactar ${url}: ${(e as Error).message}`);
  }
}

type RespuestaToken = {
  access_token: string;
  token_type: string;
  id_token: string;
  expires_in?: number;
  refresh_token?: string;
};

// Token Request: POST form-urlencoded con client_id/client_secret en HTTP Basic Auth.
export async function canjearCodigo(code: string): Promise<RespuestaToken> {
  const cfg = getAuthConfig();
  const basic = Buffer.from(
    `${encodeURIComponent(cfg.clientId)}:${encodeURIComponent(cfg.clientSecret)}`,
  ).toString("base64");

  const res = await llamarIdp(cfg.tokenEndpoint, {
    method: "POST",
    headers: {
      Authorization: `Basic ${basic}`,
      "Content-Type": "application/x-www-form-urlencoded",
      Accept: "application/json",
    },
    body: new URLSearchParams({
      grant_type: "authorization_code",
      code,
      redirect_uri: cfg.redirectUri,
    }),
  });

  const cuerpo = await res.json().catch(() => null);
  if (!res.ok || !cuerpo?.id_token || !cuerpo?.access_token) {
    throw new ErrorLogin(
      "token_invalido",
      `Token endpoint respondio ${res.status}: ${cuerpo?.error ?? ""} ${cuerpo?.error_description ?? ""}`,
    );
  }
  return cuerpo as RespuestaToken;
}

type Jwk = NodeJsonWebKey & { kid?: string };
let jwksCache: { claves: Jwk[]; vence: number } | null = null;

async function clavesJwks(forzar = false): Promise<Jwk[]> {
  if (!forzar && jwksCache && jwksCache.vence > Date.now()) return jwksCache.claves;
  const res = await llamarIdp(getAuthConfig().jwksUri, { headers: { Accept: "application/json" } });
  if (!res.ok) throw new ErrorLogin("idp_no_disponible", `JWKS respondio ${res.status}`);
  const { keys } = (await res.json()) as { keys?: Jwk[] };
  jwksCache = { claves: keys ?? [], vence: Date.now() + 10 * 60_000 };
  return jwksCache.claves;
}

// Minimo de bits de la clave RSA: 1024 solo contra el ambiente de testing de ID Uruguay;
// con cualquier otro issuer se exigen 2048.
function bitsMinimosRsa(): number {
  return new URL(getAuthConfig().issuer).hostname === "auth-testing.iduruguay.gub.uy" ? 1024 : 2048;
}

// La firma RS256 se verifica con node:crypto y no con jose: el ambiente de testing de ID Uruguay
// firma con una clave RSA de 1024 bits y jose v6 rechaza las menores a 2048.
async function verificarRs256(idToken: string, kid: string | undefined): Promise<void> {
  const [header, payload, firma] = idToken.split(".");
  const buscar = (claves: Jwk[]) =>
    claves.find((k) => k.kty === "RSA" && (!kid || k.kid === kid) && (!k.use || k.use === "sig"));
  // Si no aparece el kid, se refresca el JWKS una vez por si el IdP roto la clave.
  const jwk = buscar(await clavesJwks()) ?? buscar(await clavesJwks(true));
  if (!jwk) throw new Error(`No hay clave RSA en el JWKS para kid ${kid}`);
  const clave = createPublicKey({ key: jwk, format: "jwk" });
  const bits = clave.asymmetricKeyDetails?.modulusLength ?? 0;
  if (bits < bitsMinimosRsa()) {
    throw new Error(`clave RSA de ${bits} bits, se requieren al menos ${bitsMinimosRsa()}`);
  }
  const valida = verify(
    "RSA-SHA256",
    Buffer.from(`${header}.${payload}`),
    clave,
    Buffer.from(firma ?? "", "base64url"),
  );
  if (!valida) throw new Error("firma invalida");
}

function validarClaims(payload: JWTPayload, issuers: string[], audience: string, tolerancia: number) {
  const ahora = Math.floor(Date.now() / 1000);
  if (!payload.iss || !issuers.includes(payload.iss)) throw new Error(`iss inesperado: ${payload.iss}`);
  const aud = Array.isArray(payload.aud) ? payload.aud : [payload.aud];
  if (!aud.includes(audience)) throw new Error(`aud inesperado: ${payload.aud}`);
  if (typeof payload.exp !== "number" || payload.exp + tolerancia < ahora) throw new Error("id_token vencido");
  if (typeof payload.nbf === "number" && payload.nbf - tolerancia > ahora) throw new Error("id_token aun no valido");
}

// Valida firma (RS256 contra el JWKS, o HS256 con el client secret), iss, aud, exp y nonce.
export async function validarIdToken(idToken: string, nonceEsperado: string): Promise<JWTPayload> {
  const cfg = getAuthConfig();
  const opciones = {
    issuer: cfg.issuersAceptados,
    audience: cfg.clientId,
    clockTolerance: 60,
  };

  let payload: JWTPayload;
  try {
    const { alg, kid } = decodeProtectedHeader(idToken);
    if (alg === "HS256") {
      ({ payload } = await jwtVerify(idToken, new TextEncoder().encode(cfg.clientSecret), {
        ...opciones,
        algorithms: ["HS256"],
      }));
    } else if (alg === "RS256") {
      await verificarRs256(idToken, kid);
      payload = decodeJwt(idToken);
      validarClaims(payload, cfg.issuersAceptados,cfg.clientId, opciones.clockTolerance);
    } else {
      throw new Error(`alg no soportado: ${alg}`);
    }
  } catch (e) {
    if (e instanceof ErrorLogin) throw e;
    throw new ErrorLogin("token_invalido", `id_token invalido: ${(e as Error).message}`);
  }

  if (payload.nonce !== nonceEsperado) {
    throw new ErrorLogin("token_invalido", "El nonce del id_token no coincide");
  }
  if (!payload.sub) {
    throw new ErrorLogin("token_invalido", "El id_token no trae sub");
  }
  return payload;
}

export type ClaimsGubUy = {
  sub: string;
  nombre_completo?: string;
  primer_nombre?: string;
  segundo_nombre?: string;
  primer_apellido?: string;
  segundo_apellido?: string;
  uid?: string;
  rid?: number | string;
  email?: string;
  email_verified?: boolean;
  pais_documento?: { codigo?: string; nombre?: string } | string;
  tipo_documento?: { codigo?: number | string; nombre?: string } | string;
  numero_documento?: string;
};

export async function obtenerUserinfo(accessToken: string, subEsperado: string): Promise<ClaimsGubUy> {
  const res = await llamarIdp(getAuthConfig().userinfoEndpoint, {
    headers: { Authorization: `Bearer ${accessToken}`, Accept: "application/json" },
  });
  if (!res.ok) {
    throw new ErrorLogin("token_invalido", `Userinfo respondio ${res.status}`);
  }
  const claims = (await res.json()) as ClaimsGubUy;
  // La doc de ID Uruguay: si el sub de userinfo no coincide con el del id_token, la respuesta es invalida.
  if (claims.sub !== subEsperado) {
    throw new ErrorLogin("token_invalido", "El sub de userinfo no coincide con el del id_token");
  }
  return claims;
}

import { loadConfig } from "../config";

// Configuracion del frontoffice de funcionarios. Las URLs salen del archivo JSON (lib/config.ts) y los
// secretos de variables de entorno: en desarrollo de .env.local (ignorado por git) y en Docker del
// .env de la raiz.

function requerida(nombre: string): string {
  const valor = process.env[nombre];
  if (!valor) {
    throw new Error(`Falta la variable de entorno ${nombre} (ver .env.example)`);
  }
  return valor;
}

export function getAuthConfig() {
  const { appBaseUrl, backendUrl, backendTimeoutMs, oidc } = loadConfig();
  const issuer = oidc.issuer;

  return {
    appBaseUrl,
    // Login con ID Uruguay (gub.uy), mismo cliente OIDC que el front de ciudadanos. La redirect URI y la
    // de logout (por defecto appBaseUrl y appBaseUrl/logout) tienen que estar registradas en ID Uruguay.
    issuer,
    // Algunos tokens de testing traen el issuer sin "/v1": se aceptan los dos.
    issuersAceptados: [issuer, issuer.replace(/\/v\d+$/, "")],
    authorizationEndpoint: `${issuer}/authorize`,
    tokenEndpoint: `${issuer}/token`,
    userinfoEndpoint: `${issuer}/userinfo`,
    endSessionEndpoint: `${issuer}/logout`,
    jwksUri: `${issuer}/jwks`,
    clientId: oidc.clientId,
    // Opcional para que el sitio (y el ingreso de testing) ande aunque gub.uy no este configurado
    clientSecret: process.env.OIDC_CLIENT_SECRET ?? "",
    redirectUri: oidc.redirectUri,
    postLogoutRedirectUri: oidc.postLogoutRedirectUri,
    scopes: oidc.scopes,
    timeoutMs: oidc.timeoutMs,
    sessionSecret: requerida("SESSION_SECRET"),
    backendUrl,
    backendApiKey: requerida("BACKEND_API_KEY"),
    backendTimeoutMs,
    cookiesSeguras: appBaseUrl.startsWith("https://"),
  };
}

export type AuthConfig = ReturnType<typeof getAuthConfig>;

// Ingreso con cedula sin gub.uy: solo con LOGIN_TESTING=true (y FUNCIONARIOS_INGRESO_TESTING=true en
// WildFly). Pasa por los mismos controles del backend (funcionario registrado y PDI); apagarlo cuando el
// login con gub.uy ande en el ambiente.
export function loginTestingHabilitado(): boolean {
  return process.env.LOGIN_TESTING === "true";
}

// Los POST que abren o cierran sesion solo se aceptan desde la propia app (CSRF)
export function mismoOrigen(req: Request): boolean {
  return req.headers.get("origin") === new URL(loadConfig().appBaseUrl).origin;
}

// Configuracion del login con ID Uruguay (gub.uy). Todo sale de variables de entorno:
// en desarrollo de .env.local (ignorado por git) y en Docker del .env de la raiz.
// El client secret nunca va en el codigo.

function requerida(nombre: string): string {
  const valor = process.env[nombre];
  if (!valor) {
    throw new Error(`Falta la variable de entorno ${nombre} (ver .env.example)`);
  }
  return valor;
}

function opcional(nombre: string, porDefecto: string): string {
  return process.env[nombre] || porDefecto;
}

export function getAuthConfig() {
  const issuer = opcional("OIDC_ISSUER", "https://auth-testing.iduruguay.gub.uy/oidc/v1");
  const appBaseUrl = opcional("APP_BASE_URL", "http://localhost").replace(/\/$/, "");

  return {
    issuer,
    // Algunos tokens de testing traen el issuer sin "/v1": se aceptan los dos.
    issuersAceptados: opcional("OIDC_ISSUERS_ACEPTADOS", `${issuer},${issuer.replace(/\/v\d+$/, "")}`)
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean),
    authorizationEndpoint: opcional("OIDC_AUTHORIZATION_ENDPOINT", `${issuer}/authorize`),
    tokenEndpoint: opcional("OIDC_TOKEN_ENDPOINT", `${issuer}/token`),
    userinfoEndpoint: opcional("OIDC_USERINFO_ENDPOINT", `${issuer}/userinfo`),
    endSessionEndpoint: opcional("OIDC_END_SESSION_ENDPOINT", `${issuer}/logout`),
    jwksUri: opcional("OIDC_JWKS_URI", `${issuer}/jwks`),
    clientId: requerida("OIDC_CLIENT_ID"),
    clientSecret: requerida("OIDC_CLIENT_SECRET"),
    // Tiene que ser EXACTAMENTE una de las redirect URIs registradas en ID Uruguay
    // (http://localhost o http://localhost:8080).
    redirectUri: opcional("OIDC_REDIRECT_URI", appBaseUrl),
    postLogoutRedirectUri: opcional("OIDC_POST_LOGOUT_REDIRECT_URI", `${appBaseUrl}/logout`),
    scopes: opcional("OIDC_SCOPES", "openid personal_info email document"),
    timeoutMs: Number(opcional("OIDC_TIMEOUT_MS", "8000")),
    appBaseUrl,
    sessionSecret: requerida("SESSION_SECRET"),
    // Backend Frontofficeciudadanos (WildFly). En Docker: http://wildfly:8080/frontofficeciudadanos-web/api
    backendUrl: opcional("BACKEND_URL", "http://localhost:8080/frontofficeciudadanos-web/api").replace(/\/$/, ""),
    backendApiKey: requerida("BACKEND_API_KEY"),
    // Alcanza para los reintentos de la PDI del lado del backend (3 intentos de 5 s + esperas)
    backendTimeoutMs: Number(opcional("BACKEND_TIMEOUT_MS", "20000")),
    cookiesSeguras: appBaseUrl.startsWith("https://"),
  };
}

export type AuthConfig = ReturnType<typeof getAuthConfig>;

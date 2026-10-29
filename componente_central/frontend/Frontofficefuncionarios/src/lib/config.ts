import { readFileSync } from "node:fs";

// Configuracion no secreta del frontoffice, leida de un archivo JSON. En Docker se monta en
// /app/config/app.json (config/docker.json, ver docker-compose.yml); con npm run dev se indica otro
// archivo con CONFIG_FILE (ver .env.example). Los secretos siguen saliendo de variables de entorno.

export type AppConfig = {
  // URL publica de la app INCLUIDO el basePath (/funcionarios, ver next.config.ts). Es tambien la redirect
  // URI de gub.uy, y appBaseUrl/logout la de logout: las dos tienen que estar registradas en ID Uruguay.
  appBaseUrl: string;
  // API REST del backend Frontofficefuncionarios (WildFly), vista desde el servidor de Next
  backendUrl: string;
  backendTimeoutMs: number;
  // Login con ID Uruguay. Sin clientId (o sin OIDC_CLIENT_SECRET) el boton de gub.uy responde idp_no_disponible.
  oidc: {
    clientId: string;
    // Por defecto appBaseUrl y appBaseUrl/logout. En el docker-compose local son http://localhost:8080 y
    // http://localhost:8080/logout, las registradas para desarrollo (config/docker.json y next.config.ts).
    redirectUri: string;
    postLogoutRedirectUri: string;
    issuer: string;
    scopes: string;
    timeoutMs: number;
  };
};

const RUTA_POR_DEFECTO = "/app/config/app.json";

let cache: AppConfig | null = null;

function url(json: Record<string, unknown>, clave: string, archivo: string): string {
  const valor = json[clave];
  if (typeof valor !== "string" || !/^https?:\/\//.test(valor)) {
    throw new Error(`Falta ${clave} (URL http o https) en ${archivo}`);
  }
  return valor.replace(/\/$/, "");
}

function urlOpcional(json: Record<string, unknown>, clave: string, porDefecto: string, archivo: string): string {
  return json[clave] === undefined ? porDefecto : url(json, clave, archivo);
}

function texto(json: Record<string, unknown>, clave: string, porDefecto: string): string {
  const valor = json[clave];
  return typeof valor === "string" && valor ? valor : porDefecto;
}

function positivo(json: Record<string, unknown>, clave: string, porDefecto: number, archivo: string): number {
  const valor = json[clave] ?? porDefecto;
  if (typeof valor !== "number" || valor <= 0) {
    throw new Error(`${clave} tiene que ser un numero positivo en ${archivo}`);
  }
  return valor;
}

export function loadConfig(): AppConfig {
  if (cache) return cache;
  const archivo = process.env.CONFIG_FILE || RUTA_POR_DEFECTO;

  let json: Record<string, unknown>;
  try {
    json = JSON.parse(readFileSync(archivo, "utf8"));
  } catch (e) {
    throw new Error(`No se pudo leer la configuracion ${archivo}: ${(e as Error).message}`);
  }

  const oidc = (typeof json.oidc === "object" && json.oidc !== null ? json.oidc : {}) as Record<string, unknown>;

  const appBaseUrl = url(json, "appBaseUrl", archivo);

  cache = {
    appBaseUrl,
    backendUrl: url(json, "backendUrl", archivo),
    backendTimeoutMs: positivo(json, "backendTimeoutMs", 10000, archivo),
    oidc: {
      clientId: texto(oidc, "clientId", ""),
      redirectUri: urlOpcional(oidc, "redirectUri", appBaseUrl, archivo),
      postLogoutRedirectUri: urlOpcional(oidc, "postLogoutRedirectUri", `${appBaseUrl}/logout`, archivo),
      issuer: texto(oidc, "issuer", "https://auth-testing.iduruguay.gub.uy/oidc/v1").replace(/\/$/, ""),
      scopes: texto(oidc, "scopes", "openid personal_info email document"),
      timeoutMs: positivo(oidc, "timeoutMs", 8000, archivo),
    },
  };
  return cache;
}

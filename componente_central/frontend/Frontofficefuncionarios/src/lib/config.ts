import { readFileSync } from "node:fs";

// Configuracion no secreta del frontoffice, leida de un archivo JSON. En Docker se monta en
// /app/config/app.json (config/docker.json, ver docker-compose.yml); con npm run dev se indica otro
// archivo con CONFIG_FILE (ver .env.example). Los secretos siguen saliendo de variables de entorno.

export type AppConfig = {
  // URL publica de la app INCLUIDO el basePath (/funcionarios, ver next.config.ts)
  appBaseUrl: string;
  // API REST del backend Frontofficefuncionarios (WildFly), vista desde el servidor de Next
  backendUrl: string;
  backendTimeoutMs: number;
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

export function loadConfig(): AppConfig {
  if (cache) return cache;
  const archivo = process.env.CONFIG_FILE || RUTA_POR_DEFECTO;

  let json: Record<string, unknown>;
  try {
    json = JSON.parse(readFileSync(archivo, "utf8"));
  } catch (e) {
    throw new Error(`No se pudo leer la configuracion ${archivo}: ${(e as Error).message}`);
  }

  const timeout = json.backendTimeoutMs ?? 10000;
  if (typeof timeout !== "number" || timeout <= 0) {
    throw new Error(`backendTimeoutMs tiene que ser un numero positivo en ${archivo}`);
  }

  cache = {
    appBaseUrl: url(json, "appBaseUrl", archivo),
    backendUrl: url(json, "backendUrl", archivo),
    backendTimeoutMs: timeout,
  };
  return cache;
}

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
  const { appBaseUrl, backendUrl, backendTimeoutMs } = loadConfig();

  return {
    appBaseUrl,
    sessionSecret: requerida("SESSION_SECRET"),
    backendUrl,
    backendApiKey: requerida("BACKEND_API_KEY"),
    backendTimeoutMs,
    cookiesSeguras: appBaseUrl.startsWith("https://"),
  };
}

export type AuthConfig = ReturnType<typeof getAuthConfig>;

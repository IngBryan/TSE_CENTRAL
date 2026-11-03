import { getAuthConfig } from "./auth/config";
import { registrarLog, segundosDesde } from "./log";

// Cliente de la API REST del backend Frontofficeciudadanos (WildFly). Solo se usa del lado del
// servidor de Next: la API key nunca llega al navegador.

export class BackendNoDisponible extends Error {}

export type RespuestaBackend<T> = { status: number; data: T };

export async function llamarBackend<T>(
  ruta: string,
  opciones: { method?: "GET" | "POST" | "PUT"; body?: unknown; correlationId: string; usuarioCi?: string },
): Promise<RespuestaBackend<T>> {
  const cfg = getAuthConfig();
  const headers: Record<string, string> = {
    Accept: "application/json",
    "X-Api-Key": cfg.backendApiKey,
    "X-Correlation-Id": opciones.correlationId,
  };
  if (opciones.body !== undefined) headers["Content-Type"] = "application/json";
  // Quien hace la operacion: la cedula de la sesion (el backend controla sus permisos)
  if (opciones.usuarioCi) headers["X-Usuario-Ci"] = opciones.usuarioCi;

  const inicio = performance.now();
  const metodo = opciones.method ?? "GET";
  let res: Response;
  try {
    res = await fetch(`${cfg.backendUrl}${ruta}`, {
      method: metodo,
      headers,
      body: opciones.body === undefined ? undefined : JSON.stringify(opciones.body),
      cache: "no-store",
      signal: AbortSignal.timeout(cfg.backendTimeoutMs),
    });
  } catch (e) {
    registrarLog(
      "error",
      "backend.llamada",
      { correlationId: opciones.correlationId, usuario: opciones.usuarioCi, error: (e as Error).message, backendRoundtripSeconds: segundosDesde(inicio) },
      { metodo, ruta },
    );
    throw new BackendNoDisponible(`No se pudo contactar el backend: ${(e as Error).message}`);
  }
  registrarLog(
    res.status >= 500 ? "error" : "info",
    "backend.llamada",
    {
      correlationId: opciones.correlationId,
      usuario: opciones.usuarioCi,
      responseCode: res.status,
      backendRoundtripSeconds: segundosDesde(inicio),
    },
    { metodo, ruta },
  );
  if (res.status >= 500) {
    throw new BackendNoDisponible(`El backend respondio ${res.status}`);
  }
  return { status: res.status, data: (await res.json().catch(() => null)) as T };
}

// Para respuestas binarias (fotos de los eventos): devuelve la Response del backend tal cual.
export async function descargarBackend(
  ruta: string,
  opciones: { correlationId: string; usuarioCi: string },
): Promise<Response> {
  const cfg = getAuthConfig();
  try {
    return await fetch(`${cfg.backendUrl}${ruta}`, {
      headers: {
        "X-Api-Key": cfg.backendApiKey,
        "X-Correlation-Id": opciones.correlationId,
        "X-Usuario-Ci": opciones.usuarioCi,
      },
      cache: "no-store",
      signal: AbortSignal.timeout(cfg.backendTimeoutMs),
    });
  } catch (e) {
    throw new BackendNoDisponible(`No se pudo contactar el backend: ${(e as Error).message}`);
  }
}

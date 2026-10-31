import { getAuthConfig } from "./auth/config";

// Cliente de la API REST del backend Frontofficefuncionarios (WildFly). Solo se usa del lado del
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

  let res: Response;
  try {
    res = await fetch(`${cfg.backendUrl}${ruta}`, {
      method: opciones.method ?? "GET",
      headers,
      body: opciones.body === undefined ? undefined : JSON.stringify(opciones.body),
      cache: "no-store",
      signal: AbortSignal.timeout(cfg.backendTimeoutMs),
    });
  } catch (e) {
    throw new BackendNoDisponible(`No se pudo contactar el backend: ${(e as Error).message}`);
  }
  // Solo 502/503/504 son "no disponible" (proxy, WildFly arrancando, timeout). Un 500 es un error del
  // backend: se registra y la pagina muestra que no se pudo hacer la operacion.
  if (res.status === 502 || res.status === 503 || res.status === 504) {
    throw new BackendNoDisponible(`El backend respondio ${res.status}`);
  }
  if (res.status >= 500) {
    console.error(`[backend] correlationId=${opciones.correlationId} ${opciones.method ?? "GET"} ${ruta} status=${res.status}`);
  }
  return { status: res.status, data: (await res.json().catch(() => null)) as T };
}

// GET de un recurso binario (la foto de un evento de viaje): devuelve la respuesta tal cual, para pasar
// el cuerpo al navegador sin leerlo entero en memoria. Igual que en Frontofficeciudadanos.
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

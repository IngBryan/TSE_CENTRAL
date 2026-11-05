
import { randomUUID } from "node:crypto";
import { cookies } from "next/headers";

import { registrarLog, segundosDesde } from "./log";

export const NOMBRE_COOKIE_SESION = "tse_backoffice_sesion";
export const RUTA_COOKIE_SESION = "/backoffice";

export type IdentidadBackoffice = {
  ci: string;
  rol: "ADMINISTRADOR" | "AUTORIDAD";
};

export function urlBackend(ruta: string): string {
  const base = process.env.BACKOFFICE_API_URL;

  if (!base) {
    throw new Error("BACKOFFICE_API_URL no está configurada");
  }

  return `${base.replace(/\/$/, "")}/${ruta.replace(/^\//, "")}`;
}

// Toda llamada al backend: le pone el identificador de correlacion y deja el log con lo que tardo
export async function fetchBackend(
  url: string,
  init: Omit<RequestInit, "headers"> & { headers?: Record<string, string> } = {}
): Promise<Response> {
  const correlationId = randomUUID();
  const inicio = performance.now();
  const datos = { metodo: init.method ?? "GET", ruta: new URL(url).pathname };

  try {
    const respuesta = await fetch(url, {
      ...init,
      headers: { ...init.headers, "X-Correlation-Id": correlationId },
    });

    registrarLog(
      respuesta.status >= 500 ? "error" : "info",
      "backend.llamada",
      {
        correlationId,
        responseCode: respuesta.status,
        backendRoundtripSeconds: segundosDesde(inicio),
      },
      datos
    );

    return respuesta;
  } catch (e) {
    registrarLog(
      "error",
      "backend.llamada",
      {
        correlationId,
        error: (e as Error).message,
        backendRoundtripSeconds: segundosDesde(inicio),
      },
      datos
    );

    throw e;
  }
}

export async function idSesion(): Promise<string | null> {
  const valor = (await cookies()).get(NOMBRE_COOKIE_SESION)?.value;

  return valor && /^[A-Za-z0-9_.~-]+$/.test(valor)
    ? valor
    : null;
}

// WildFly keeps no session: every request carries the token (JWT) of the login, so any replica can validate it
export function wildFlyAuthorization(token: string): string {
  return `Bearer ${token}`;
}

export async function consultarIdentidad():
  Promise<IdentidadBackoffice | null> {

  const sesion = await idSesion();
  if (!sesion) return null;

  try {
    const respuesta = await fetchBackend(urlBackend("auth/me"), {
      cache: "no-store",
      headers: {
        Accept: "application/json",
        Authorization: wildFlyAuthorization(sesion),
      },
    });

    if (!respuesta.ok) return null;

    const datos: unknown = await respuesta.json();

    if (
      !datos ||
      typeof datos !== "object" ||
      !("ci" in datos) ||
      typeof datos.ci !== "string" ||
      !("rol" in datos) ||
      (datos.rol !== "ADMINISTRADOR" &&
        datos.rol !== "AUTORIDAD")
    ) {
      return null;
    }

    return { ci: datos.ci, rol: datos.rol };
  } catch {
    return null;
  }
}

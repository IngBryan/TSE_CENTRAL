
import { cookies } from "next/headers";

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
    const respuesta = await fetch(urlBackend("auth/me"), {
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

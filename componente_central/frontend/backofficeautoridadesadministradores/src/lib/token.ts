import { esRol, type Rol } from "./rutas";

export const NOMBRE_COOKIE_SESION = "tse_backoffice_sesion";
export const RUTA_COOKIE_SESION = "/backoffice";

/**
 * Reads the role and expiration of the backoffice JWT without checking its signature (only WildFly knows the
 * secret). Good enough for optimistic redirects; WildFly still validates the token on every request.
 */
export function leerToken(token: string | undefined): { ci: string; rol: Rol } | null {
  if (!token || !/^[A-Za-z0-9_.~-]+$/.test(token)) return null;

  const partes = token.split(".");
  if (partes.length !== 3) return null;

  try {
    const payload: unknown = JSON.parse(Buffer.from(partes[1], "base64url").toString("utf8"));

    if (
      !payload ||
      typeof payload !== "object" ||
      !("sub" in payload) ||
      typeof payload.sub !== "string" ||
      !("rol" in payload) ||
      !esRol(payload.rol) ||
      !("exp" in payload) ||
      typeof payload.exp !== "number" ||
      payload.exp * 1000 <= Date.now()
    ) {
      return null;
    }

    return { ci: payload.sub, rol: payload.rol };
  } catch {
    return null;
  }
}

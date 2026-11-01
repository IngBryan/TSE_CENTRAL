
import {
  idSesion,
  urlBackend,
  wildFlyAuthorization,
} from "./backoffice-sesion";

export type TipoPermiso = {
  id: number;
  nombre: string;
  pideNumero: boolean;
  pideVencimiento: boolean;
  activo: boolean;
};

export type DatosTipoPermiso = Omit<TipoPermiso, "id">;

type ResultadoListado =
  | { ok: true; tipos: TipoPermiso[] }
  | { ok: false; mensaje: string };

type ResultadoGuardado =
  | { ok: true }
  | { ok: false; mensaje: string };

const SESION_VENCIDA = "La sesión venció. Vuelve a iniciar sesión.";
const SIN_CONEXION = "No se pudo establecer conexión con el backend.";

/**
 * Consulta el catálogo de tipos de permiso a demanda.
 */
export async function listarTiposPermiso(): Promise<ResultadoListado> {
  const sesion = await idSesion();

  if (!sesion) {
    return { ok: false, mensaje: SESION_VENCIDA };
  }

  try {
    const respuesta = await fetch(urlBackend("tipos-permiso"), {
      method: "GET",
      headers: {
        Accept: "application/json",
        Authorization: wildFlyAuthorization(sesion),
      },
      cache: "no-store",
    });

    if (respuesta.status === 401) {
      return { ok: false, mensaje: SESION_VENCIDA };
    }

    if (respuesta.status === 403) {
      return {
        ok: false,
        mensaje: "No tenés permisos para consultar los tipos de permiso.",
      };
    }

    if (!respuesta.ok) {
      return {
        ok: false,
        mensaje: "No se pudo obtener el listado de tipos de permiso.",
      };
    }

    return { ok: true, tipos: (await respuesta.json()) as TipoPermiso[] };
  } catch {
    return { ok: false, mensaje: SIN_CONEXION };
  }
}

/**
 * Da de alta un tipo de permiso, o lo modifica si se indica su id.
 * Esta función debe ejecutarse desde el servidor Next.js.
 */
export async function guardarTipoPermiso(
  datos: DatosTipoPermiso,
  id?: number
): Promise<ResultadoGuardado> {
  const sesion = await idSesion();

  if (!sesion) {
    return { ok: false, mensaje: SESION_VENCIDA };
  }

  try {
    const respuesta = await fetch(
      urlBackend(id === undefined ? "tipos-permiso" : `tipos-permiso/${id}`),
      {
        method: id === undefined ? "POST" : "PUT",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
          Authorization: wildFlyAuthorization(sesion),
        },
        body: JSON.stringify(datos),
        cache: "no-store",
      }
    );

    if (respuesta.ok) {
      return { ok: true };
    }

    if (respuesta.status === 401) {
      return { ok: false, mensaje: SESION_VENCIDA };
    }

    if (respuesta.status === 403) {
      return {
        ok: false,
        mensaje: "No tenés permisos para administrar los tipos de permiso.",
      };
    }

    try {
      const error: unknown = await respuesta.json();

      if (
        error !== null &&
        typeof error === "object" &&
        "detalle" in error &&
        typeof error.detalle === "string"
      ) {
        return { ok: false, mensaje: error.detalle };
      }
    } catch {
      // Se utilizará el mensaje genérico.
    }

    return { ok: false, mensaje: "No se pudo guardar el tipo de permiso." };
  } catch {
    return { ok: false, mensaje: SIN_CONEXION };
  }
}

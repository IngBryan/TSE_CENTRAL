
import type { FiltrosUsuarios, PaginaUsuarios } from "./usuarios";
import {
  fetchBackend,
  idSesion,
  urlBackend,
  wildFlyAuthorization,
} from "./backoffice-sesion";

type ResultadoListado =
  | { ok: true; pagina: PaginaUsuarios }
  | { ok: false; mensaje: string };

const SESION_VENCIDA = "La sesión venció. Vuelve a iniciar sesión.";

/**
 * Consulta una página del listado de usuarios
 * mediante la API REST del Backoffice.
 */
export async function listarUsuarios(
  pagina: number,
  tamanio: number,
  filtros: FiltrosUsuarios
): Promise<ResultadoListado> {
  const sesion = await idSesion();

  if (!sesion) {
    return { ok: false, mensaje: SESION_VENCIDA };
  }

  const parametros = new URLSearchParams({
    pagina: String(pagina),
    tamanio: String(tamanio),
  });

  if (filtros.rol) parametros.set("rol", filtros.rol);
  if (filtros.ci) parametros.set("ci", filtros.ci);

  try {
    const respuesta = await fetchBackend(
      urlBackend(`usuarios?${parametros}`),
      {
        method: "GET",
        headers: {
          Accept: "application/json",
          Authorization: wildFlyAuthorization(sesion),
        },
        cache: "no-store",
      }
    );

    if (respuesta.status === 401) {
      return { ok: false, mensaje: SESION_VENCIDA };
    }

    if (respuesta.status === 403) {
      return {
        ok: false,
        mensaje: "No tenés permisos para consultar los usuarios.",
      };
    }

    // Filtro invalido (por ejemplo una cedula con letras): el backend explica el motivo
    if (respuesta.status === 400) {
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
    }

    if (!respuesta.ok) {
      return {
        ok: false,
        mensaje: "No se pudo obtener el listado de usuarios.",
      };
    }

    const datos: unknown = await respuesta.json();

    if (
      datos === null ||
      typeof datos !== "object" ||
      !("usuarios" in datos) ||
      !Array.isArray(datos.usuarios)
    ) {
      return {
        ok: false,
        mensaje: "La respuesta del backend no es válida.",
      };
    }

    return { ok: true, pagina: datos as PaginaUsuarios };
  } catch {
    return {
      ok: false,
      mensaje: "No se pudo establecer conexión con el backend.",
    };
  }
}

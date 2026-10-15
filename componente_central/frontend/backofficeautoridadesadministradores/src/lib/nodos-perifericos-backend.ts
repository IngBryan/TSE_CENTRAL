
import type { AltaNodo, DatosNodo } from "./nodos-perifericos";


type ResultadoListado =
  | { ok: true; nodos: DatosNodo[] }
  | { ok: false; mensaje: string };

/**
 * Consulta los nodos periféricos registrados
 * mediante la API REST del Backoffice.
 */
export async function listarNodos(): Promise<ResultadoListado> {
  const baseUrl = process.env.BACKOFFICE_API_URL;

  if (!baseUrl) {
    return {
      ok: false,
      mensaje: "No se configuró la URL del backend.",
    };
  }

  try {
    const respuesta = await fetch(
      `${baseUrl.replace(/\/$/, "")}/nodos-perifericos`,
      {
        method: "GET",
        headers: {
          Accept: "application/json",
        },
        cache: "no-store",
      }
    );

    if (respuesta.status === 401) {
      return {
        ok: false,
        mensaje: "Se requiere autenticación.",
      };
    }

    if (respuesta.status === 403) {
      return {
        ok: false,
        mensaje: "No tenés permisos para consultar los nodos.",
      };
    }

    if (!respuesta.ok) {
      return {
        ok: false,
        mensaje: "No se pudo obtener el listado de nodos.",
      };
    }

    const datos: unknown = await respuesta.json();

    if (!Array.isArray(datos)) {
      return {
        ok: false,
        mensaje: "La respuesta del backend no es válida.",
      };
    }

    return {
      ok: true,
      nodos: datos as DatosNodo[],
    };
  } catch {
    return {
      ok: false,
      mensaje: "No se pudo establecer conexión con el backend.",
    };
  }
}


type ResultadoAlta =
  | { ok: true }
  | { ok: false; mensaje: string };

/**
 * Registra un nodo periférico mediante la API REST.
 * Esta función debe ejecutarse desde el servidor Next.js.
 */
export async function registrarNodo(
  datos: AltaNodo
): Promise<ResultadoAlta> {
  const baseUrl = process.env.BACKOFFICE_API_URL;

  if (!baseUrl) {
    return {
      ok: false,
      mensaje: "No se configuró la URL del backend.",
    };
  }

  try {
    const respuesta = await fetch(
      `${baseUrl.replace(/\/$/, "")}/nodos-perifericos`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify(datos),
        cache: "no-store",
      }
    );

    if (respuesta.ok) {
      return { ok: true };
    }

    if (respuesta.status === 401) {
      return {
        ok: false,
        mensaje: "Se requiere autenticación.",
      };
    }

    if (respuesta.status === 403) {
      return {
        ok: false,
        mensaje: "No tenés permisos para registrar nodos.",
      };
    }

    if (respuesta.status === 400 || respuesta.status === 409) {
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

    return {
      ok: false,
      mensaje:
        respuesta.status === 409
          ? "El nodo ya está registrado."
          : "No se pudo registrar el nodo.",
    };
  } catch {
    return {
      ok: false,
      mensaje: "No se pudo establecer conexión con el backend.",
    };
  }
}

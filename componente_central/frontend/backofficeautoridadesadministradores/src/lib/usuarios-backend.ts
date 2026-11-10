
import type {
  AltaUsuario,
  CambioContrasena,
  CambioRol,
  FiltrosUsuarios,
  PaginaUsuarios,
} from "./usuarios";
import {
  fetchBackend,
  idSesion,
  urlBackend,
  wildFlyAuthorization,
} from "./backoffice-sesion";

type Fallo = { ok: false; mensaje: string };

type ResultadoListado = { ok: true; pagina: PaginaUsuarios } | Fallo;

type ResultadoAlta = { ok: true } | Fallo;

const SESION_VENCIDA = "La sesión venció. Vuelve a iniciar sesión.";

type Pedido = {
  ruta: string;
  method: "GET" | "POST" | "PUT" | "DELETE";
  cuerpo?: unknown;
  sinPermisos: string;
  // Los status cuyo cuerpo explica el motivo (campo detalle)
  conDetalle: number[];
  generico: (status: number) => string;
};

async function detalle(respuesta: Response): Promise<string | null> {
  try {
    const error: unknown = await respuesta.json();

    return error !== null &&
      typeof error === "object" &&
      "detalle" in error &&
      typeof error.detalle === "string"
      ? error.detalle
      : null;
  } catch {
    return null;
  }
}

/**
 * Llama a la API REST del Backoffice con la sesión actual. Se ejecuta en el
 * servidor de Next.js: la contraseña nunca pasa por otro lado que no sea el
 * backend.
 */
async function pedir(
  pedido: Pedido
): Promise<{ ok: true; respuesta: Response } | Fallo> {
  const sesion = await idSesion();

  if (!sesion) {
    return { ok: false, mensaje: SESION_VENCIDA };
  }

  const headers: Record<string, string> = {
    Accept: "application/json",
    Authorization: wildFlyAuthorization(sesion),
  };

  if (pedido.cuerpo !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  let respuesta: Response;

  try {
    respuesta = await fetchBackend(urlBackend(pedido.ruta), {
      method: pedido.method,
      headers,
      body:
        pedido.cuerpo === undefined ? undefined : JSON.stringify(pedido.cuerpo),
      cache: "no-store",
    });
  } catch {
    return {
      ok: false,
      mensaje: "No se pudo establecer conexión con el backend.",
    };
  }

  if (respuesta.ok) {
    return { ok: true, respuesta };
  }

  if (respuesta.status === 401) {
    return { ok: false, mensaje: SESION_VENCIDA };
  }

  if (respuesta.status === 403) {
    return { ok: false, mensaje: pedido.sinPermisos };
  }

  const explicado = pedido.conDetalle.includes(respuesta.status)
    ? await detalle(respuesta)
    : null;

  return { ok: false, mensaje: explicado ?? pedido.generico(respuesta.status) };
}

/**
 * Consulta una página del listado de usuarios.
 */
export async function listarUsuarios(
  pagina: number,
  tamanio: number,
  filtros: FiltrosUsuarios
): Promise<ResultadoListado> {
  const parametros = new URLSearchParams({
    pagina: String(pagina),
    tamanio: String(tamanio),
  });

  if (filtros.rol) parametros.set("rol", filtros.rol);
  if (filtros.ci) parametros.set("ci", filtros.ci);

  const resultado = await pedir({
    ruta: `usuarios?${parametros}`,
    method: "GET",
    sinPermisos: "No tenés permisos para consultar los usuarios.",
    // Filtro invalido (por ejemplo una cedula con letras)
    conDetalle: [400],
    generico: () => "No se pudo obtener el listado de usuarios.",
  });

  if (!resultado.ok) {
    return resultado;
  }

  const datos: unknown = await resultado.respuesta.json().catch(() => null);

  if (
    datos === null ||
    typeof datos !== "object" ||
    !("usuarios" in datos) ||
    !Array.isArray(datos.usuarios)
  ) {
    return { ok: false, mensaje: "La respuesta del backend no es válida." };
  }

  return { ok: true, pagina: datos as PaginaUsuarios };
}

/**
 * Da de alta un administrador, una autoridad o un funcionario.
 */
export async function crearUsuario(
  datos: AltaUsuario
): Promise<ResultadoAlta> {
  const resultado = await pedir({
    ruta: "usuarios",
    method: "POST",
    cuerpo: datos,
    sinPermisos: "No tenés permisos para crear usuarios.",
    conDetalle: [400, 409],
    generico: (status) =>
      status === 409 ? "El usuario ya existe." : "No se pudo crear el usuario.",
  });

  return resultado.ok ? { ok: true } : resultado;
}

/**
 * Reemplaza el rol de un funcionario, administrador o autoridad.
 */
export async function cambiarRol(
  ci: string,
  datos: CambioRol
): Promise<ResultadoAlta> {
  const resultado = await pedir({
    ruta: `usuarios/${encodeURIComponent(ci)}/rol`,
    method: "PUT",
    cuerpo: datos,
    sinPermisos: "No tenés permisos para cambiar roles.",
    // 400 datos, 404 no es usuario interno, 409 ya tiene el rol o es el propio
    conDetalle: [400, 404, 409],
    generico: () => "No se pudo cambiar el rol.",
  });

  return resultado.ok ? { ok: true } : resultado;
}

/**
 * Cambia la contraseña del usuario logueado (la cédula sale del token).
 */
export async function cambiarContrasena(
  datos: CambioContrasena
): Promise<ResultadoAlta> {
  const resultado = await pedir({
    ruta: "auth/contrasena",
    method: "PUT",
    cuerpo: datos,
    sinPermisos: "No tenés permisos para cambiar la contraseña.",
    // 400 la nueva no es válida o la actual no es correcta, 404 ya no es usuario del backoffice
    conDetalle: [400, 404],
    generico: () => "No se pudo cambiar la contraseña.",
  });

  return resultado.ok ? { ok: true } : resultado;
}

/**
 * Elimina a un funcionario, administrador o autoridad (un funcionario
 * queda inactivo).
 */
export async function eliminarUsuario(ci: string): Promise<ResultadoAlta> {
  const resultado = await pedir({
    ruta: `usuarios/${encodeURIComponent(ci)}`,
    method: "DELETE",
    sinPermisos: "No tenés permisos para eliminar usuarios.",
    // 400 cedula, 404 no es usuario interno, 409 es el propio administrador
    conDetalle: [400, 404, 409],
    generico: () => "No se pudo eliminar el usuario.",
  });

  return resultado.ok ? { ok: true } : resultado;
}

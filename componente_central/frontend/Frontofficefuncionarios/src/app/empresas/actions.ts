"use server";

import { randomUUID } from "node:crypto";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import type { ErrorBackend } from "@/lib/casos";
import type { AltaEmpresa, CambiosEmpresa, OperacionRealizada, PermisoEmpresa, UsuarioDisponible } from "@/lib/empresas";

const NO_DISPONIBLE = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";
const SESION_VENCIDA = "Tu sesión venció. Volvé a ingresar.";

// desactualizada: otro funcionario modifico la empresa despues de que se cargo; la pagina se recarga con los
// datos actuales
export type ResultadoModificacion =
  | { ok: true; mensaje: string }
  | { ok: false; error: string; campos?: Record<string, string>; desactualizada?: boolean };

export type ResultadoAlta = { ok: false; error: string; campos?: Record<string, string> };

export type ResultadoUsuarios = { ok: true; usuarios: UsuarioDisponible[] } | { ok: false; error: string };

// Opciones del desplegable de rubro (catalogo precargado). El rubro es opcional: si falla, queda solo "Sin rubro".
export async function obtenerRubros(): Promise<string[]> {
  const sesion = await getSesion();
  if (!sesion) return [];
  try {
    const res = await llamarBackend<string[]>("/empresas/rubros", { correlationId: randomUUID(), usuarioCi: sesion.usuario.cedula });
    return res.status === 200 && Array.isArray(res.data) ? res.data : [];
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return [];
  }
}

// Permisos a demanda que se le pueden exigir a la empresa. null si no se pudieron obtener.
export async function obtenerPermisos(nroEmpresa: number): Promise<PermisoEmpresa[] | null> {
  const sesion = await getSesion();
  if (!sesion) return null;
  try {
    const res = await llamarBackend<PermisoEmpresa[]>(`/empresas/${nroEmpresa}/permisos`, {
      correlationId: randomUUID(),
      usuarioCi: sesion.usuario.cedula,
    });
    return res.status === 200 && Array.isArray(res.data) ? res.data : null;
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return null;
  }
}

// Reemplaza los permisos a demanda asignados: los vehiculos de la empresa los van a tener que cargar
export async function asignarPermisos(nroEmpresa: number, tipos: number[]): Promise<ResultadoModificacion> {
  const sesion = await getSesion();
  if (!sesion) return { ok: false, error: SESION_VENCIDA };
  if (!Number.isSafeInteger(nroEmpresa) || nroEmpresa <= 0) return { ok: false, error: "La empresa no existe." };
  if (!Array.isArray(tipos) || !tipos.every(Number.isSafeInteger)) {
    return { ok: false, error: "No se pudieron guardar los permisos." };
  }
  const correlationId = randomUUID();
  try {
    const res = await llamarBackend<PermisoEmpresa[] & ErrorBackend>(`/empresas/${nroEmpresa}/permisos`, {
      method: "PUT",
      body: { tipos },
      correlationId,
      usuarioCi: sesion.usuario.cedula,
    });
    console.info(`[empresas] correlationId=${correlationId} PUT /empresas/${nroEmpresa}/permisos status=${res.status}`);
    if (res.status === 200) {
      revalidatePath(`/empresas/${nroEmpresa}`);
      return { ok: true, mensaje: "Guardamos los permisos de la empresa." };
    }
    if (res.status === 400) {
      // Un tipo elegido se desactivo mientras tanto: la pagina se recarga con los disponibles
      revalidatePath(`/empresas/${nroEmpresa}`);
      return { ok: false, error: "Alguno de los permisos ya no está disponible. Revisá la lista." };
    }
    if (res.status === 404) return { ok: false, error: "La empresa no existe." };
    if (res.status === 403)
      return { ok: false, error: "No podés asignar permisos: no estás registrado como funcionario del MTOP." };
    return { ok: false, error: "No se pudieron guardar los permisos." };
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return { ok: false, error: NO_DISPONIBLE };
  }
}

// FA4, paso 2: usuarios registrados y validados que se pueden elegir como responsable. filtro es el
// comienzo de la cedula (vacio: los primeros).
export async function buscarUsuariosDisponibles(filtro: string): Promise<ResultadoUsuarios> {
  const sesion = await getSesion();
  if (!sesion) return { ok: false, error: SESION_VENCIDA };
  if (typeof filtro !== "string" || !/^\d{0,8}$/.test(filtro)) {
    return { ok: false, error: "Buscá por el comienzo de la cédula (solo dígitos)." };
  }
  const correlationId = randomUUID();
  const ruta = `/empresas/usuarios-disponibles${filtro ? `?ci=${filtro}` : ""}`;
  try {
    const res = await llamarBackend<UsuarioDisponible[] & ErrorBackend>(ruta, {
      correlationId,
      usuarioCi: sesion.usuario.cedula,
    });
    console.info(`[empresas] correlationId=${correlationId} GET ${ruta} status=${res.status}`);
    if (res.status === 200 && Array.isArray(res.data)) return { ok: true, usuarios: res.data };
    if (res.status === 403) return { ok: false, error: "No estás registrado como funcionario del MTOP." };
    return { ok: false, error: "No se pudieron obtener los usuarios registrados." };
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return { ok: false, error: NO_DISPONIBLE };
  }
}

// CU Gestionar empresas de transporte, FA1: alta de la empresa con su responsable (opcional). Si sale
// bien redirige al detalle de la empresa nueva; si no, devuelve el error (y el campo al que corresponde).
export async function crearEmpresa(datos: AltaEmpresa): Promise<ResultadoAlta> {
  const sesion = await getSesion();
  if (!sesion) return { ok: false, error: SESION_VENCIDA };
  if (
    !Number.isSafeInteger(datos?.nroEmpresa) ||
    typeof datos.nombrePublico !== "string" ||
    typeof datos.razonSocial !== "string" ||
    typeof datos.direccion !== "string" ||
    typeof datos.habilitada !== "boolean" ||
    (datos.rubro !== null && typeof datos.rubro !== "string") ||
    (datos.ciResponsable !== null && typeof datos.ciResponsable !== "string")
  ) {
    return { ok: false, error: "No se pudo registrar la empresa." };
  }
  const body: AltaEmpresa = {
    nroEmpresa: datos.nroEmpresa,
    nombrePublico: datos.nombrePublico,
    razonSocial: datos.razonSocial,
    direccion: datos.direccion,
    rubro: datos.rubro,
    habilitada: datos.habilitada,
    ciResponsable: datos.ciResponsable,
  };
  const correlationId = randomUUID();
  let res;
  try {
    res = await llamarBackend<OperacionRealizada & ErrorBackend>("/empresas", {
      method: "POST",
      body,
      correlationId,
      usuarioCi: sesion.usuario.cedula,
    });
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return { ok: false, error: NO_DISPONIBLE };
  }
  console.info(`[empresas] correlationId=${correlationId} POST /empresas status=${res.status}`);
  if (res.status === 200) {
    revalidatePath("/empresas");
    // Fuera del try: redirect() funciona tirando una excepcion que Next tiene que recibir
    redirect(`/empresas/${body.nroEmpresa}?creada=1`);
  }
  const detalle = res.data?.detalle;
  const codigo = res.data?.error;
  if (res.status === 400) return { ok: false, error: "Revisá los datos marcados.", campos: res.data?.campos };
  if (codigo === "EMPRESA_EXISTENTE") {
    return { ok: false, error: "Revisá los datos marcados.", campos: { nroEmpresa: detalle ?? "El número ya existe." } };
  }
  // El usuario elegido dejo de estar disponible (otro funcionario lo asocio mientras tanto, por ejemplo)
  if (codigo === "RESPONSABLE_DE_OTRA_EMPRESA" || codigo === "USUARIO_NO_VALIDADO" || codigo === "USUARIO_NO_ENCONTRADO") {
    return {
      ok: false,
      error: "Revisá los datos marcados.",
      campos: { ciResponsable: `${detalle ?? "El usuario no se puede asociar."} Elegí otro usuario.` },
    };
  }
  if (res.status === 401) {
    console.error(`[empresas] correlationId=${correlationId} el backend rechazo la X-Api-Key: revisar BACKEND_API_KEY`);
  }
  if (res.status === 403)
    return { ok: false, error: "No podés registrar empresas: no estás registrado como funcionario del MTOP." };
  return { ok: false, error: "No se pudo registrar la empresa." };
}

// CU Gestionar empresas de transporte, FA3: modificar los datos de la empresa. La cedula sale de la
// sesion; el backend controla que sea funcionario (FA5) y valida los datos (400 con los campos).
export async function modificarEmpresa(nroEmpresa: number, cambios: CambiosEmpresa): Promise<ResultadoModificacion> {
  const sesion = await getSesion();
  if (!sesion) return { ok: false, error: SESION_VENCIDA };
  if (!Number.isSafeInteger(nroEmpresa) || nroEmpresa <= 0) return { ok: false, error: "La empresa no existe." };
  if (
    typeof cambios?.nombrePublico !== "string" ||
    typeof cambios.razonSocial !== "string" ||
    typeof cambios.direccion !== "string" ||
    typeof cambios.habilitada !== "boolean" ||
    (cambios.rubro !== null && typeof cambios.rubro !== "string") ||
    !Number.isSafeInteger(cambios.version)
  ) {
    return { ok: false, error: "No se pudo modificar la empresa." };
  }
  const body: CambiosEmpresa = {
    nombrePublico: cambios.nombrePublico,
    razonSocial: cambios.razonSocial,
    direccion: cambios.direccion,
    rubro: cambios.rubro,
    habilitada: cambios.habilitada,
    version: cambios.version,
  };
  const correlationId = randomUUID();
  try {
    const res = await llamarBackend<OperacionRealizada & ErrorBackend>(`/empresas/${nroEmpresa}`, {
      method: "PUT",
      body,
      correlationId,
      usuarioCi: sesion.usuario.cedula,
    });
    console.info(`[empresas] correlationId=${correlationId} PUT /empresas/${nroEmpresa} status=${res.status}`);
    if (res.status === 200) {
      revalidatePath(`/empresas/${nroEmpresa}`);
      revalidatePath("/empresas");
      return { ok: true, mensaje: res.data?.mensaje ?? "La empresa fue modificada." };
    }
    if (res.status === 400) return { ok: false, error: "Revisá los datos marcados.", campos: res.data?.campos };
    if (res.status === 404) return { ok: false, error: "La empresa no existe." };
    if (res.data?.error === "EMPRESA_DESACTUALIZADA") {
      // Otro funcionario la modifico mientras tanto: la pagina se vuelve a cargar con los datos actuales
      revalidatePath(`/empresas/${nroEmpresa}`);
      return {
        ok: false,
        error: "Otro funcionario modificó la empresa mientras tanto. Arriba se muestran los datos actuales.",
        desactualizada: true,
      };
    }
    if (res.status === 401) {
      console.error(`[empresas] correlationId=${correlationId} el backend rechazo la X-Api-Key: revisar BACKEND_API_KEY`);
    }
    if (res.status === 403)
      return { ok: false, error: "No podés modificar empresas: no estás registrado como funcionario del MTOP." };
    return { ok: false, error: "No se pudo modificar la empresa." };
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return { ok: false, error: NO_DISPONIBLE };
  }
}

// yaTieneResponsable: otro funcionario le asocio uno mientras tanto; la pagina se recarga con el actual y el
// panel de asociacion se cierra, asi que el aviso lo tiene que mostrar quien lo abrio. noDisponible: el
// usuario elegido ya no se puede asociar (no esta validado, es responsable de otra empresa o no existe); los
// demas errores (sesion, backend caido) no dicen nada del usuario y se puede reintentar con el mismo.
export type ResultadoAsociacion =
  | { ok: true; mensaje: string }
  | { ok: false; error: string; yaTieneResponsable?: boolean; noDisponible?: boolean };

// CU Gestionar empresas de transporte, FA4: asociar un usuario registrado y validado como responsable de
// una empresa que todavia no lo tiene. El backend controla que sea funcionario y las reglas de la asociacion.
export async function asociarResponsable(nroEmpresa: number, ci: string): Promise<ResultadoAsociacion> {
  const sesion = await getSesion();
  if (!sesion) return { ok: false, error: SESION_VENCIDA };
  if (!Number.isSafeInteger(nroEmpresa) || nroEmpresa <= 0) return { ok: false, error: "La empresa no existe." };
  if (typeof ci !== "string" || !/^\d{6,8}$/.test(ci)) return { ok: false, error: "Elegí un usuario de la lista." };
  const correlationId = randomUUID();
  try {
    const res = await llamarBackend<OperacionRealizada & ErrorBackend>(`/empresas/${nroEmpresa}/responsable`, {
      method: "POST",
      body: { ci },
      correlationId,
      usuarioCi: sesion.usuario.cedula,
    });
    console.info(`[empresas] correlationId=${correlationId} POST /empresas/${nroEmpresa}/responsable status=${res.status}`);
    if (res.status === 200) {
      revalidatePath(`/empresas/${nroEmpresa}`);
      revalidatePath("/empresas");
      return { ok: true, mensaje: res.data?.mensaje ?? "El usuario fue asociado a la empresa." };
    }
    const codigo = res.data?.error;
    const detalle = res.data?.detalle;
    if (codigo === "EMPRESA_NO_ENCONTRADA") return { ok: false, error: "La empresa no existe." };
    if (codigo === "EMPRESA_CON_RESPONSABLE") {
      // Otro funcionario le asocio un responsable mientras tanto: la pagina se vuelve a cargar con el actual
      revalidatePath(`/empresas/${nroEmpresa}`);
      return {
        ok: false,
        error: "Otro funcionario le asoció un responsable a la empresa mientras tanto.",
        yaTieneResponsable: true,
      };
    }
    if (codigo === "RESPONSABLE_DE_OTRA_EMPRESA" || codigo === "USUARIO_NO_VALIDADO" || codigo === "USUARIO_NO_ENCONTRADO") {
      return {
        ok: false,
        error: `${detalle ?? "El usuario no se puede asociar."} Elegí otro usuario.`,
        noDisponible: true,
      };
    }
    if (res.status === 400) return { ok: false, error: "Elegí un usuario de la lista." };
    if (res.status === 401) {
      console.error(`[empresas] correlationId=${correlationId} el backend rechazo la X-Api-Key: revisar BACKEND_API_KEY`);
    }
    if (res.status === 403)
      return { ok: false, error: "No podés asociar responsables: no estás registrado como funcionario del MTOP." };
    return { ok: false, error: "No se pudo asociar el responsable." };
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return { ok: false, error: NO_DISPONIBLE };
  }
}

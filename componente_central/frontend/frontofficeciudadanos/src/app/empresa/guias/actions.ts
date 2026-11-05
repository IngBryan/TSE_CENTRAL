"use server";

import { randomUUID } from "node:crypto";
import { revalidatePath } from "next/cache";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import { getSesion } from "@/lib/auth/session";
import type { Previsualizacion } from "./[id]/rutas/tipos-rutas";
import { mensajeErrorRutas, type ErrorRuta } from "@/lib/mensajes-rutas";

// Gestion de Viajes de Carga y CU-07. La cedula del responsable sale de la sesion; el backend
// controla permisos y reglas.

export type VehiculoResumen = { matricula: string; marca: string; modelo: string; capacidadCargaKg: number };
export type ChoferResumen = { id: number; cedula: string; primerNombre?: string; primerApellido?: string };
export type Guia = {
  id: string;
  rubroCliente: string;
  tipoCarga?: string | null;
  pesoCargaKg: number;
  fecha: string;
  origen: string;
  destino: string;
  origenId?: number | null;
  destinoId?: number | null;
  origenDepartamento?: string | null;
  destinoDepartamento?: string | null;
  estado: "SIN_ASIGNAR" | "ASIGNADA" | "EN_CURSO" | "FINALIZADA" | "CANCELADA";
  vehiculo?: VehiculoResumen | null;
  chofer?: ChoferResumen | null;
  version?: number | null;
};

export type ValoresGuia = { rubroCliente: string; tipoCarga: string; pesoCargaKg: string; fecha: string; origenId: string; destinoId: string };
export type EstadoNuevaGuia = { ok?: string; error?: string; campos?: Record<string, string>; valores?: ValoresGuia };
export type ResultadoAsignacion = { ok: true } | { ok: false; error: string; campos?: Record<string, string> };

type ErrorBackend = { detalle?: string; campos?: Record<string, string> };

async function llamar<T>(ruta: string, method: "GET" | "POST" | "PUT", body?: unknown) {
  const sesion = await getSesion();
  if (!sesion) return null;
  const correlationId = randomUUID();
  const res = await llamarBackend<T & ErrorBackend>(ruta, { method, body, correlationId, usuarioCi: sesion.perfil.cedula });
  console.info(`[guias] correlationId=${correlationId} ${method} ${ruta} status=${res.status}`);
  return res;
}

export async function crearGuia(_previo: EstadoNuevaGuia, formulario: FormData): Promise<EstadoNuevaGuia> {
  const valores: ValoresGuia = {
    rubroCliente: String(formulario.get("rubroCliente") ?? ""),
    tipoCarga: String(formulario.get("tipoCarga") ?? ""),
    pesoCargaKg: String(formulario.get("pesoCargaKg") ?? ""),
    fecha: String(formulario.get("fecha") ?? ""),
    origenId: String(formulario.get("origenId") ?? ""),
    destinoId: String(formulario.get("destinoId") ?? ""),
  };
  const carga = Number(valores.pesoCargaKg);
  try {
    const res = await llamar<Guia>("/empresa/guias", "POST", {
      ...valores,
      pesoCargaKg: Number.isFinite(carga) && valores.pesoCargaKg !== "" ? Math.trunc(carga) : null,
      origenId: /^\d+$/.test(valores.origenId) ? Number(valores.origenId) : null,
      destinoId: /^\d+$/.test(valores.destinoId) ? Number(valores.destinoId) : null,
    });
    if (!res) return { error: "Tu sesión venció. Volvé a ingresar con gub.uy.", valores };
    if (res.status === 400) return { error: "Revisá los datos marcados.", campos: res.data?.campos, valores };
    if (res.status === 403) return { error: `No podés ingresar guías: ${res.data?.detalle ?? "no sos responsable"}.`, valores };
    if (res.status !== 200 || !res.data) return { error: "No se pudo guardar la guía.", valores };
    revalidatePath("/empresa/guias");
    return { ok: res.data.id };
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return { error: "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.", valores };
  }
}

// CU-07 pasos 8 y 9
export async function asignarGuia(
  idGuia: string, matricula: string, idChofer: number,
  rutas: Pick<Previsualizacion, "origenId" | "destinoId" | "previsualizacionId">,
): Promise<ResultadoAsignacion> {
  if (!rutas || !Number.isSafeInteger(rutas.origenId) || !Number.isSafeInteger(rutas.destinoId)
      || rutas.origenId <= 0 || rutas.destinoId <= 0 || rutas.origenId === rutas.destinoId
      || !/^[a-f0-9]{64}$/.test(rutas.previsualizacionId)) {
    return { ok: false, error: "Previsualizá las rutas del viaje antes de continuar." };
  }
  try {
    const res = await llamar<Guia & NonNullable<ErrorRuta>>(`/empresa/guias/${encodeURIComponent(idGuia)}/asignacion`, "POST", {
      matricula, idChofer,
      rutas: { origenId: rutas.origenId, destinoId: rutas.destinoId, previsualizacionId: rutas.previsualizacionId },
    });
    if (!res) return { ok: false, error: "Tu sesión venció. Volvé a ingresar con gub.uy." };
    if (res.status === 200) {
      revalidatePath("/empresa/guias");
      revalidatePath(`/empresa/guias/${encodeURIComponent(idGuia)}`);
      revalidatePath(`/empresa/guias/${encodeURIComponent(idGuia)}/rutas`);
      return { ok: true };
    }
    if (res.data?.error && res.data.error !== "datos_invalidos" && res.data.error !== "GUIA_YA_ASIGNADA"
        && res.status !== 403 && res.status !== 404) {
      return { ok: false, error: mensajeErrorRutas(res.status, res.data) };
    }
    if (res.status === 400) return { ok: false, error: "Revisá la selección.", campos: res.data?.campos };
    if (res.status === 409) return { ok: false, error: res.data?.detalle ?? "La guía ya está asignada." };
    if (res.status === 404) return { ok: false, error: "La guía no existe." };
    if (res.status === 403) return { ok: false, error: `No podés asignar guías: ${res.data?.detalle ?? "no sos responsable"}.` };
    return { ok: false, error: "No se pudo registrar la asignación." };
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return { ok: false, error: "El servicio no está disponible en este momento. Probá de nuevo en unos minutos." };
  }
}

// ===== Modificacion de una guia ASIGNADA (editar, reasignar, cancelar) =====
// version: la de la guia que se estaba mirando; si cambio entretanto, el backend responde 409
// GUIA_DESACTUALIZADA y hay que recargar.

export type DatosEdicion = Omit<ValoresGuia, "pesoCargaKg" | "origenId" | "destinoId"> & { pesoCargaKg: number | null; origenId: number | null; destinoId: number | null };
export type ResultadoModificacion = { ok: true } | { ok: false; error: string; campos?: Record<string, string> };

async function modificar(idGuia: string, ruta: string, method: "POST" | "PUT", body: unknown): Promise<ResultadoModificacion> {
  try {
    const res = await llamar<Guia>(`/empresa/guias/${encodeURIComponent(idGuia)}${ruta}`, method, body);
    if (!res) return { ok: false, error: "Tu sesión venció. Volvé a ingresar con gub.uy." };
    if (res.status === 200) {
      revalidatePath("/empresa/guias");
      revalidatePath(`/empresa/guias/${idGuia}`);
      return { ok: true };
    }
    if (res.status === 400) return { ok: false, error: res.data?.campos?.general ?? "Revisá los datos marcados.", campos: res.data?.campos };
    if (res.status === 409) return { ok: false, error: res.data?.detalle ?? "La guía ya no se puede modificar." };
    if (res.status === 404) return { ok: false, error: "La guía no existe." };
    if (res.status === 403) return { ok: false, error: `No podés modificar guías: ${res.data?.detalle ?? "no sos responsable"}.` };
    return { ok: false, error: "No se pudo guardar el cambio." };
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return { ok: false, error: "El servicio no está disponible en este momento. Probá de nuevo en unos minutos." };
  }
}

export async function editarGuia(idGuia: string, version: number, datos: DatosEdicion): Promise<ResultadoModificacion> {
  return modificar(idGuia, "", "PUT", { ...datos, version });
}

export async function reasignarGuia(idGuia: string, version: number, matricula: string, idChofer: number): Promise<ResultadoModificacion> {
  return modificar(idGuia, "/reasignacion", "POST", { matricula, idChofer, version });
}

export async function cancelarGuia(idGuia: string, version: number, motivo: string): Promise<ResultadoModificacion> {
  return modificar(idGuia, "/cancelacion", "POST", { motivo, version });
}

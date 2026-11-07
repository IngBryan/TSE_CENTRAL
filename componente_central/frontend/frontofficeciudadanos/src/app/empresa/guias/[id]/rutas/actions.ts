"use server";

import { randomUUID } from "node:crypto";
import { revalidatePath } from "next/cache";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import { mensajeErrorRutas, type ErrorRuta } from "@/lib/mensajes-rutas";
import type { Previsualizacion, RespuestaRutas, RutaAsignada } from "./tipos-rutas";

export async function previsualizarRutas(origenId: number, destinoId: number): Promise<RespuestaRutas<Previsualizacion>> {
  const sesion = await getSesion();
  if (!sesion) return { ok: false, error: "La sesión venció. Volvé a ingresar." };
  if (!Number.isSafeInteger(origenId) || !Number.isSafeInteger(destinoId) || origenId <= 0 || destinoId <= 0 || origenId === destinoId) {
    return { ok: false, error: "Seleccioná dos localidades diferentes." };
  }
  try {
    const params = new URLSearchParams({ origenId: String(origenId), destinoId: String(destinoId) });
    const res = await llamarBackend<Previsualizacion & NonNullable<ErrorRuta>>(`/rutas/previsualizacion?${params}`, {
      correlationId: randomUUID(), usuarioCi: sesion.perfil.cedula,
    });
    if (res.status !== 200 || !res.data) return { ok: false, error: mensajeErrorRutas(res.status, res.data) };
    return { ok: true, datos: res.data };
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return { ok: false, error: "El servicio de cálculo de rutas no está disponible." };
  }
}

export async function confirmarRutas(
  idGuia: string, origenId: number, destinoId: number, previsualizacionId: string,
): Promise<RespuestaRutas<RutaAsignada[]>> {
  const sesion = await getSesion();
  if (!sesion) return { ok: false, error: "La sesión venció. Volvé a ingresar." };
  if (!idGuia || idGuia.length > 120 || !Number.isSafeInteger(origenId) || !Number.isSafeInteger(destinoId)
      || origenId <= 0 || destinoId <= 0 || origenId === destinoId || !/^[a-f0-9]{64}$/.test(previsualizacionId)) {
    return { ok: false, error: "Primero debés previsualizar las rutas de origen y destino elegidos." };
  }
  try {
    const res = await llamarBackend<RutaAsignada[] & NonNullable<ErrorRuta>>(`/empresa/guias/${encodeURIComponent(idGuia)}/rutas`, {
      method: "POST", body: { origenId, destinoId, previsualizacionId },
      correlationId: randomUUID(), usuarioCi: sesion.perfil.cedula,
    });
    if (res.status !== 200 || !res.data) return { ok: false, error: mensajeErrorRutas(res.status, res.data) };
    revalidatePath(`/empresa/guias/${encodeURIComponent(idGuia)}/rutas`);
    revalidatePath(`/empresa/guias/${encodeURIComponent(idGuia)}`);
    return { ok: true, datos: res.data };
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return { ok: false, error: "No se pudo contactar al servicio de asignación de rutas." };
  }
}

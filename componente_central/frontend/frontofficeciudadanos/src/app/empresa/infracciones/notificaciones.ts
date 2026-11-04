"use server";

import { randomUUID } from "node:crypto";
import { revalidatePath } from "next/cache";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import type { Notificaciones } from "@/lib/infracciones";

// Notificaciones de casos de fiscalizacion para la campana del encabezado. Solo las ve el responsable de la
// empresa: la cedula sale de la sesion y el backend controla que sea responsable. Si no se pueden obtener
// (no es responsable, backend caido) se devuelve null y la campana no se muestra o conserva lo que tenia.

async function llamar(ruta: string, method: "GET" | "POST"): Promise<Notificaciones | null> {
  const sesion = await getSesion();
  if (!sesion?.perfil.roles?.some((r) => r.rol === "RESPONSABLE")) return null;
  const correlationId = randomUUID();
  try {
    const res = await llamarBackend<Notificaciones>(ruta, { method, correlationId, usuarioCi: sesion.perfil.cedula });
    return res.status === 200 ? res.data : null;
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return null;
  }
}

export async function obtenerNotificaciones() {
  return llamar("/empresa/infracciones/notificaciones", "GET");
}

export async function marcarNotificacionVista(idCaso: number) {
  if (!Number.isSafeInteger(idCaso) || idCaso <= 0) return null;
  const r = await llamar(`/empresa/infracciones/${idCaso}/vista`, "POST");
  revalidatePath("/empresa/infracciones");
  return r;
}

export async function marcarTodasVistas() {
  const r = await llamar("/empresa/infracciones/notificaciones/vistas", "POST");
  revalidatePath("/empresa/infracciones");
  return r;
}

"use server";

import { randomUUID } from "node:crypto";
import { revalidatePath } from "next/cache";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import { getSesion } from "@/lib/auth/session";
import { registrarLog } from "@/lib/log";

// CU-02 Dar de alta chofer. La cedula del responsable sale SIEMPRE de la sesion (cookie cifrada),
// nunca del formulario; el backend controla que sea responsable validado de una empresa habilitada.

export type ResultadoAlta =
  | "CONFIRMAR"
  | "YA_ES_CHOFER"
  | "NO_EXISTE"
  | "MENOR_DE_EDAD"
  | "ASOCIADO_PENDIENTE"
  | "ALTA_CONFIRMADA"
  | "DEBE_CONSULTAR";

export type RespuestaAlta =
  | { ok: true; resultado: ResultadoAlta; cedula: string; primerNombre?: string; primerApellido?: string }
  | { ok: false; error: "sin_sesion" | "cedula_invalida" | "no_autorizado" | "no_disponible"; detalle?: string };

async function llamar(ruta: string, cedula: string): Promise<RespuestaAlta> {
  const sesion = await getSesion();
  if (!sesion) return { ok: false, error: "sin_sesion" };
  const ci = cedula.replace(/\D/g, "");
  if (!/^\d{6,8}$/.test(ci)) return { ok: false, error: "cedula_invalida" };

  const correlationId = randomUUID();
  try {
    const res = await llamarBackend<{ resultado: ResultadoAlta; primerNombre?: string; primerApellido?: string; detalle?: string }>(
      ruta,
      { method: "POST", body: { cedula: ci }, correlationId, usuarioCi: sesion.perfil.cedula },
    );
    if (res.status === 403) return { ok: false, error: "no_autorizado", detalle: res.data?.detalle };
    if (res.status === 400) return { ok: false, error: "cedula_invalida" };
    if (res.status !== 200 || !res.data) return { ok: false, error: "no_disponible" };
    return { ok: true, cedula: ci, ...res.data };
  } catch (e) {
    if (e instanceof BackendNoDisponible) {
      registrarLog("warn", "choferes", { correlationId }, { detalle: `${e.message}` });
      return { ok: false, error: "no_disponible" };
    }
    throw e;
  }
}

// Pasos 2 a 7
export async function consultarChofer(cedula: string): Promise<RespuestaAlta> {
  const r = await llamar("/empresa/choferes/consulta", cedula);
  if (r.ok && r.resultado === "ASOCIADO_PENDIENTE") revalidatePath("/empresa/choferes");
  return r;
}

// Pasos 8 a 10
export async function confirmarChofer(cedula: string): Promise<RespuestaAlta> {
  const r = await llamar("/empresa/choferes", cedula);
  if (r.ok) revalidatePath("/empresa/choferes");
  return r;
}

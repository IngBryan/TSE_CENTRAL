"use server";

import { randomUUID } from "node:crypto";
import { revalidatePath } from "next/cache";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import {
  DECISIONES,
  type Decision,
  type ErrorBackend,
  type ResolucionRegistrada,
} from "@/lib/casos";
import { registrarLog } from "@/lib/log";

export type ResultadoResolucion =
  | { ok: true; mensaje: string }
  | { ok: false; error: string; campos?: Record<string, string> };

// CU Gestionar casos de fiscalizacion, FA5: confirmar o desestimar el caso con fundamento. La cedula
// sale de la sesion; el backend controla que sea funcionario (FA1) y que la transicion este prevista
// (FA6: 409 TRANSICION_NO_PERMITIDA).
export async function resolverCaso(
  idCaso: number,
  decision: Decision,
  fundamento: string,
): Promise<ResultadoResolucion> {
  const sesion = await getSesion();
  if (!sesion)
    return {
      ok: false,
      error: "Tu sesión venció. Volvé a ingresar.",
    };
  if (!Number.isSafeInteger(idCaso) || idCaso <= 0)
    return { ok: false, error: "El caso de fiscalización no existe." };
  if (
    typeof decision !== "string" ||
    !Object.hasOwn(DECISIONES, decision) ||
    typeof fundamento !== "string"
  ) {
    return { ok: false, error: "No se pudo registrar la resolución." };
  }
  const correlationId = randomUUID();
  try {
    const res = await llamarBackend<ResolucionRegistrada & ErrorBackend>(
      `/casos/${idCaso}/resolucion`,
      {
        method: "POST",
        body: { decision, fundamento },
        correlationId,
        usuarioCi: sesion.usuario.cedula,
      },
    );
    if (res.status === 200) {
      revalidatePath(`/casos/${idCaso}`);
      return {
        ok: true,
        mensaje: res.data?.mensaje ?? "La resolución fue registrada.",
      };
    }
    if (res.status === 400)
      return {
        ok: false,
        error: "Revisá los datos marcados.",
        campos: res.data?.campos,
      };
    if (res.status === 409) {
      return {
        ok: false,
        error: `${res.data?.detalle ?? "El caso ya no se puede resolver."} Recargá la página para ver su estado actual.`,
      };
    }
    if (res.status === 404)
      return { ok: false, error: "El caso de fiscalización no existe." };
    if (res.status === 401) {
      registrarLog("error", "casos", { correlationId }, { detalle: `el backend rechazo la X-Api-Key: revisar BACKEND_API_KEY` });
    }
    if (res.status === 403)
      return {
        ok: false,
        error:
          "No podés resolver casos: no estás registrado como funcionario del MTOP.",
      };
    return { ok: false, error: "No se pudo registrar la resolución." };
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return {
      ok: false,
      error:
        "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.",
    };
  }
}

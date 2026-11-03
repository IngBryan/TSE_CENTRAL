"use server";

import { randomUUID } from "node:crypto";
import { revalidatePath } from "next/cache";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import type { InfraccionDetalle } from "@/lib/infracciones";
import { registrarLog } from "@/lib/log";

// Presentar el descargo de una infraccion. La cedula del responsable sale de la sesion; el backend controla
// que el caso sea de su empresa, que este notificado, que no tenga descargo y que no haya vencido el plazo.

export type EstadoDescargo = { ok?: boolean; error?: string; campo?: string; texto?: string };

type ErrorBackend = { detalle?: string; campos?: Record<string, string> };

export async function presentarDescargo(
  idCaso: number,
  _previo: EstadoDescargo,
  formulario: FormData,
): Promise<EstadoDescargo> {
  const texto = String(formulario.get("descripcion") ?? "");
  const sesion = await getSesion();
  if (!sesion) return { error: "Tu sesión venció. Volvé a ingresar con gub.uy.", texto };
  if (!Number.isSafeInteger(idCaso) || idCaso <= 0) return { error: "La infracción no existe.", texto };

  const correlationId = randomUUID();
  try {
    const res = await llamarBackend<InfraccionDetalle & ErrorBackend>(`/empresa/infracciones/${idCaso}/descargo`, {
      method: "POST",
      body: { descripcion: texto },
      correlationId,
      usuarioCi: sesion.perfil.cedula,
    });
    registrarLog("info", "infracciones", { correlationId, responseCode: res.status }, { detalle: `POST /empresa/infracciones/${idCaso}/descargo` });
    if (res.status === 200) {
      revalidatePath("/empresa/infracciones");
      revalidatePath(`/empresa/infracciones/${idCaso}`);
      return { ok: true };
    }
    if (res.status === 400) return { error: "Revisá el descargo.", campo: res.data?.campos?.descripcion, texto };
    if (res.status === 409) return { error: res.data?.detalle ?? "La infracción ya no admite descargo.", texto };
    if (res.status === 404) return { error: "La infracción no existe.", texto };
    if (res.status === 403) {
      return { error: `No podés presentar descargos: ${res.data?.detalle ?? "no sos responsable de la empresa"}.`, texto };
    }
    return { error: "No se pudo presentar el descargo.", texto };
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return { error: "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.", texto };
  }
}

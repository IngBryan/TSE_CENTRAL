"use server";

import { randomUUID } from "node:crypto";
import { revalidatePath } from "next/cache";
import { cookies } from "next/headers";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import { COOKIE_SESION, getSesion, opcionesCookie, sellar } from "@/lib/auth/session";
import { registrarLog } from "@/lib/log";

// Perfil de la Empresa. La cedula del responsable sale de la sesion, nunca del formulario; el
// backend controla que sea responsable validado de la empresa.

export type DatosEmpresa = {
  nroEmpresa: number;
  nombrePublico: string;
  razonSocial: string;
  direccion: string;
  habilitada: boolean;
};

export type EstadoFormulario = {
  ok?: boolean;
  error?: string;
  campos?: Record<string, string>;
  valores?: { nombrePublico: string; razonSocial: string; direccion: string };
};

export async function actualizarEmpresa(_previo: EstadoFormulario, formulario: FormData): Promise<EstadoFormulario> {
  const sesion = await getSesion();
  if (!sesion) return { error: "Tu sesión venció. Volvé a ingresar con gub.uy." };

  const valores = {
    nombrePublico: String(formulario.get("nombrePublico") ?? ""),
    razonSocial: String(formulario.get("razonSocial") ?? ""),
    direccion: String(formulario.get("direccion") ?? ""),
  };
  const correlationId = randomUUID();
  try {
    const res = await llamarBackend<DatosEmpresa & { detalle?: string; campos?: Record<string, string> }>("/empresa", {
      method: "PUT",
      body: valores,
      correlationId,
      usuarioCi: sesion.perfil.cedula,
    });
    registrarLog("info", "empresa", { correlationId, responseCode: res.status }, { detalle: `actualizar` });
    if (res.status === 400) return { error: "Revisá los datos marcados.", campos: res.data?.campos, valores };
    if (res.status === 403) return { error: `No podés modificar la empresa: ${res.data?.detalle ?? "no sos su responsable"}.`, valores };
    if (res.status !== 200 || !res.data) return { error: "No se pudieron guardar los cambios.", valores };

    // El nombre de la empresa tambien esta en los roles de la sesion (encabezado, Choferes)
    const empresa = res.data;
    const roles = (sesion.perfil.roles ?? []).map((r) =>
      r.nroEmpresa === empresa.nroEmpresa ? { ...r, empresa: empresa.nombrePublico } : r,
    );
    const restante = Math.max(1, sesion.exp - Math.floor(Date.now() / 1000));
    (await cookies()).set(COOKIE_SESION, sellar({ ...sesion, perfil: { ...sesion.perfil, roles } }), opcionesCookie(restante));
    revalidatePath("/empresa");
    revalidatePath("/mi-cuenta");
    return {
      ok: true,
      valores: { nombrePublico: empresa.nombrePublico, razonSocial: empresa.razonSocial, direccion: empresa.direccion },
    };
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    registrarLog("warn", "empresa", { correlationId }, { detalle: `${e.message}` });
    return { error: "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.", valores };
  }
}

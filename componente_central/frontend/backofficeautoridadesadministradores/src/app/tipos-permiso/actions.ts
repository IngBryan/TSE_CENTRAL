
"use server";

import { revalidatePath } from "next/cache";

import {
  guardarTipoPermiso,
  type DatosTipoPermiso,
} from "@/lib/tipos-permiso-backend";

export type ResultadoTipoPermiso =
  | { ok: true }
  | { ok: false; mensaje: string };

export async function guardarTipoPermisoAction(
  datos: DatosTipoPermiso,
  id?: number
): Promise<ResultadoTipoPermiso> {
  const nombre = typeof datos?.nombre === "string" ? datos.nombre.trim() : "";

  if (!nombre || nombre.length > 100) {
    return {
      ok: false,
      mensaje: "El nombre es obligatorio y admite hasta 100 caracteres.",
    };
  }

  const resultado = await guardarTipoPermiso(
    {
      nombre,
      pideNumero: datos.pideNumero === true,
      pideVencimiento: datos.pideVencimiento === true,
      activo: datos.activo === true,
    },
    id
  );

  if (resultado.ok) {
    revalidatePath("/tipos-permiso");
  }

  return resultado;
}

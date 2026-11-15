"use server";

import { revalidatePath } from "next/cache";
import { consultarIdentidad } from "@/lib/backoffice-sesion";
import { obtenerPlazoDescargo } from "@/lib/plazo-descargo-backend";
import type { EstadoPlazoDescargo } from "@/lib/plazo-descargo";

export async function guardarPlazoDescargo(_previo: EstadoPlazoDescargo, data: FormData): Promise<EstadoPlazoDescargo> {
  const identidad = await consultarIdentidad();
  const error = (mensaje: string): EstadoPlazoDescargo => ({ estado: "error", mensaje });
  if (identidad?.rol !== "ADMINISTRADOR") return error("Iniciá sesión como administrador para continuar.");
  const numero = (campo: string) => {
    const valor = data.get(campo);
    return typeof valor === "string" && valor.trim() !== "" ? Number(valor) : Number.NaN;
  };
  const revisionBase = numero("revisionBase");
  const plazoDescargoDias = numero("plazoDescargoDias");
  const valor = data.get("fundamento");
  const fundamento = typeof valor === "string" ? valor.trim() : "";
  if (!Number.isSafeInteger(revisionBase) || revisionBase < 1) return error("Recargá la configuración antes de guardar.");
  if (!Number.isInteger(plazoDescargoDias) || plazoDescargoDias < 1 || plazoDescargoDias > 365)
    return error("El plazo debe ser un entero entre 1 y 365 días.");
  if (!fundamento || fundamento.length > 500) return error("El fundamento es obligatorio y admite hasta 500 caracteres.");
  const resultado = await obtenerPlazoDescargo({ revisionBase, plazoDescargoDias, fundamento });
  if (!resultado.ok) return { estado: "error", mensaje: resultado.mensaje, conflicto: resultado.conflicto };
  revalidatePath("/reglas-fiscalizacion");
  return { estado: "exito", mensaje: "Plazo para presentar descargo guardado.", configuracion: resultado.configuracion };
}

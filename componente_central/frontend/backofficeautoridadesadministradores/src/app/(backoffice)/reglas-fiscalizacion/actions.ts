"use server";

import { revalidatePath } from "next/cache";
import { consultarIdentidad } from "@/lib/backoffice-sesion";
import { obtenerReglas } from "@/lib/reglas-fiscalizacion-backend";
import type { EstadoReglas } from "@/lib/reglas-fiscalizacion";

export async function guardarReglas(_previo: EstadoReglas, data: FormData): Promise<EstadoReglas> {
  const identidad = await consultarIdentidad();
  if (identidad?.rol !== "ADMINISTRADOR") return { estado: "error", mensaje: "Iniciá sesión como administrador para continuar." };
  const numero = (campo: string): number => {
    const valor = data.get(campo);
    return typeof valor === "string" && valor.trim() !== "" ? Number(valor) : Number.NaN;
  };
  const revisionBase = numero("revisionBase");
  const toleranciaCargaPorcentaje = numero("toleranciaCargaPorcentaje");
  const toleranciaCargaKg = numero("toleranciaCargaKg");
  const distanciaDesvioMetros = numero("distanciaDesvioMetros");
  const ventanaDesvioSegundos = numero("ventanaDesvioSegundos");
  const valor = data.get("fundamento");
  const fundamento = typeof valor === "string" ? valor.trim() : "";
  const error = (mensaje: string): EstadoReglas => ({ estado: "error", mensaje });
  if (!Number.isSafeInteger(revisionBase) || revisionBase < 1) return error("Recargá la configuración antes de guardar.");
  if (!Number.isFinite(toleranciaCargaPorcentaje) || toleranciaCargaPorcentaje < 0 || toleranciaCargaPorcentaje > 20
      || Math.abs(toleranciaCargaPorcentaje * 100 - Math.round(toleranciaCargaPorcentaje * 100)) > 1e-8)
    return error("La tolerancia porcentual debe estar entre 0 y 20, con hasta dos decimales.");
  if (!Number.isInteger(toleranciaCargaKg) || toleranciaCargaKg < 0 || toleranciaCargaKg > 5000)
    return error("El margen de carga debe estar entre 0 y 5000 kg.");
  if (!Number.isInteger(distanciaDesvioMetros) || distanciaDesvioMetros < 1 || distanciaDesvioMetros > 10000)
    return error("La distancia debe estar entre 1 y 10000 metros.");
  if (!Number.isInteger(ventanaDesvioSegundos) || ventanaDesvioSegundos < 1 || ventanaDesvioSegundos > 600)
    return error("La ventana debe estar entre 1 y 600 segundos.");
  if (!fundamento || fundamento.length > 500) return error("El fundamento es obligatorio y admite hasta 500 caracteres.");
  const resultado = await obtenerReglas({ revisionBase, toleranciaCargaPorcentaje, toleranciaCargaKg,
    distanciaDesvioMetros, ventanaDesvioSegundos, fundamento });
  if (!resultado.ok) return { estado: "error", mensaje: resultado.mensaje, conflicto: resultado.conflicto };
  revalidatePath("/reglas-fiscalizacion");
  return { estado: "exito", mensaje: "Configuración guardada. La actualización se distribuirá automáticamente.",
    configuracion: resultado.configuracion };
}


"use server";

import { revalidatePath } from "next/cache";

import { registrarNodo } from "@/lib/nodos-perifericos-backend";
import type { EstadoAltaNodo } from "@/lib/nodos-perifericos";

export async function registrarNodoAction(
  _estadoAnterior: EstadoAltaNodo,
  formData: FormData
): Promise<EstadoAltaNodo> {
  const tipo = formData.get("tipo");

  const nombreValor = formData.get("nombre");
  const ubicacionValor = formData.get("ubicacion");
  const idExternoValor = formData.get("idExterno");

  const nombre =
    typeof nombreValor === "string" ? nombreValor.trim() : "";

  const ubicacion =
    typeof ubicacionValor === "string"
      ? ubicacionValor.trim()
      : "";

  const idExternoTexto =
    typeof idExternoValor === "string"
      ? idExternoValor.trim()
      : "";

  const error = (mensaje: string): EstadoAltaNodo => ({
    estado: "error",
    mensaje,
  });

  if (tipo !== "BALANZA" && tipo !== "TRACKING") {
    return error("Seleccioná un tipo de nodo válido.");
  }

  if (!nombre || nombre.length > 100) {
    return error("El nombre es obligatorio y admite hasta 100 caracteres.");
  }

  if (!ubicacion || ubicacion.length > 200) {
    return error("La ubicación es obligatoria y admite hasta 200 caracteres.");
  }

  if (!/^\d+$/.test(idExternoTexto)) {
    return error("El identificador externo debe ser un entero no negativo.");
  }

  const idExterno = Number(idExternoTexto);

  if (!Number.isSafeInteger(idExterno)) {
    return error("El identificador externo está fuera del rango permitido.");
  }

  const resultado = await registrarNodo({
    tipo,
    nombre,
    ubicacion,
    idExterno,
  });

  if (!resultado.ok) {
    return error(resultado.mensaje);
  }

  revalidatePath("/nodos-perifericos");

  return {
    estado: "exito",
    mensaje: "Nodo periférico registrado correctamente.",
  };
}

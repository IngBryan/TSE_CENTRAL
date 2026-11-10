"use server";

import { cambiarContrasena } from "@/lib/usuarios-backend";
import {
  LARGO_MAXIMO_CONTRASENA,
  LARGO_MINIMO_CONTRASENA,
  type EstadoAltaUsuario,
} from "@/lib/usuarios";

function texto(formData: FormData, campo: string): string {
  const valor = formData.get(campo);
  return typeof valor === "string" ? valor : "";
}

const error = (mensaje: string): EstadoAltaUsuario => ({
  estado: "error",
  mensaje,
});

/**
 * Cambia la contraseña del usuario logueado. Valida lo mismo que el backend
 * (CambioContrasenaService) para no hacer el viaje si ya se sabe que falla;
 * la contraseña actual la verifica solo el backend. Sin trim: los espacios
 * son parte de la contraseña.
 */
export async function cambiarContrasenaAction(
  _estadoAnterior: EstadoAltaUsuario,
  formData: FormData
): Promise<EstadoAltaUsuario> {
  const actual = texto(formData, "actual");
  const nueva = texto(formData, "nueva");
  const largo = [...nueva].length;

  if (!actual) {
    return error("Ingresá tu contraseña actual.");
  }

  if (largo < LARGO_MINIMO_CONTRASENA || largo > LARGO_MAXIMO_CONTRASENA) {
    return error(
      `La contraseña nueva tiene que tener entre ${LARGO_MINIMO_CONTRASENA} y ${LARGO_MAXIMO_CONTRASENA} caracteres.`
    );
  }

  if (nueva !== texto(formData, "confirmacion")) {
    return error("Las contraseñas nuevas no coinciden.");
  }

  if (nueva === actual) {
    return error("La contraseña nueva tiene que ser distinta de la actual.");
  }

  const resultado = await cambiarContrasena({
    contrasenaActual: actual,
    contrasenaNueva: nueva,
  });

  if (!resultado.ok) {
    return error(resultado.mensaje);
  }

  return {
    estado: "exito",
    mensaje: "Contraseña cambiada. Usala la próxima vez que inicies sesión.",
  };
}

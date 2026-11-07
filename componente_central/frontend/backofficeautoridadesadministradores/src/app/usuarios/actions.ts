
"use server";

import { revalidatePath } from "next/cache";

import { crearUsuario } from "@/lib/usuarios-backend";
import {
  LARGO_MAXIMO_CONTRASENA,
  LARGO_MINIMO_CONTRASENA,
  TIPOS_ALTA,
  usaContrasena,
  type AltaUsuario,
  type EstadoAltaUsuario,
  type TipoAltaUsuario,
} from "@/lib/usuarios";

function texto(formData: FormData, campo: string): string {
  const valor = formData.get(campo);
  return typeof valor === "string" ? valor : "";
}

export async function crearUsuarioAction(
  _estadoAnterior: EstadoAltaUsuario,
  formData: FormData
): Promise<EstadoAltaUsuario> {
  const error = (mensaje: string): EstadoAltaUsuario => ({
    estado: "error",
    mensaje,
  });

  const tipo = texto(formData, "tipo");

  if (!TIPOS_ALTA.includes(tipo as TipoAltaUsuario)) {
    return error("Seleccioná un tipo de usuario válido.");
  }

  const tipoAlta = tipo as TipoAltaUsuario;
  const ci = texto(formData, "ci").replace(/[.\-\s]/g, "");

  if (!/^\d{6,8}$/.test(ci)) {
    return error("La cédula tiene que tener de 6 a 8 dígitos.");
  }

  let datos: AltaUsuario;

  if (usaContrasena(tipoAlta)) {
    // Sin trim: los espacios son parte de la contraseña
    const contrasena = texto(formData, "contrasena");
    const largo = [...contrasena].length;

    if (largo < LARGO_MINIMO_CONTRASENA || largo > LARGO_MAXIMO_CONTRASENA) {
      return error(
        `La contraseña tiene que tener entre ${LARGO_MINIMO_CONTRASENA} y ${LARGO_MAXIMO_CONTRASENA} caracteres.`
      );
    }

    if (contrasena !== texto(formData, "confirmacion")) {
      return error("Las contraseñas no coinciden.");
    }

    datos = { tipo: tipoAlta, ci, contrasena };
  } else {
    const email = texto(formData, "email").trim();

    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email) || email.length > 254) {
      return error("Ingresá un email válido.");
    }

    datos = { tipo: tipoAlta, ci, email };
  }

  const resultado = await crearUsuario(datos);

  if (!resultado.ok) {
    return error(resultado.mensaje);
  }

  revalidatePath("/usuarios");

  return {
    estado: "exito",
    mensaje: `Usuario ${ci} creado correctamente.`,
  };
}


"use server";

import { revalidatePath } from "next/cache";

import {
  cambiarRol,
  crearUsuario,
  eliminarUsuario,
} from "@/lib/usuarios-backend";
import {
  LARGO_MAXIMO_CONTRASENA,
  LARGO_MAXIMO_EMAIL,
  LARGO_MINIMO_CONTRASENA,
  TIPOS_ALTA,
  usaContrasena,
  type AltaUsuario,
  type CambioRol,
  type EstadoAltaUsuario,
  type TipoAltaUsuario,
} from "@/lib/usuarios";

function texto(formData: FormData, campo: string): string {
  const valor = formData.get(campo);
  return typeof valor === "string" ? valor : "";
}

const error = (mensaje: string): EstadoAltaUsuario => ({
  estado: "error",
  mensaje,
});

function tipoInterno(valor: string): TipoAltaUsuario | null {
  return TIPOS_ALTA.includes(valor as TipoAltaUsuario)
    ? (valor as TipoAltaUsuario)
    : null;
}

// La contraseña, o el mensaje de error. Sin trim: los espacios son parte de la contraseña
function leerContrasena(formData: FormData): { contrasena: string } | { error: string } {
  const contrasena = texto(formData, "contrasena");
  const largo = [...contrasena].length;

  if (largo < LARGO_MINIMO_CONTRASENA || largo > LARGO_MAXIMO_CONTRASENA) {
    return {
      error: `La contraseña tiene que tener entre ${LARGO_MINIMO_CONTRASENA} y ${LARGO_MAXIMO_CONTRASENA} caracteres.`,
    };
  }

  if (contrasena !== texto(formData, "confirmacion")) {
    return { error: "Las contraseñas no coinciden." };
  }

  return { contrasena };
}

// Algo@dominio.algo, sin espacios y con una sola arroba (como ValidacionUsuario del backend). Sin regex: la
// equivalente hace backtracking
function esEmail(email: string): boolean {
  const arroba = email.indexOf("@");

  if (arroba <= 0 || arroba !== email.lastIndexOf("@") || /\s/.test(email)) {
    return false;
  }

  const dominio = email.slice(arroba + 1);
  const punto = dominio.indexOf(".", 1);

  return punto > 0 && punto < dominio.length - 1;
}

function leerEmail(formData: FormData): { email: string } | { error: string } {
  const email = texto(formData, "email").trim();

  if (email.length > LARGO_MAXIMO_EMAIL || !esEmail(email)) {
    return { error: "Ingresá un email válido." };
  }

  return { email };
}

export async function crearUsuarioAction(
  _estadoAnterior: EstadoAltaUsuario,
  formData: FormData
): Promise<EstadoAltaUsuario> {
  const tipoAlta = tipoInterno(texto(formData, "tipo"));

  if (!tipoAlta) {
    return error("Seleccioná un tipo de usuario válido.");
  }

  const ci = texto(formData, "ci").replace(/[.\-\s]/g, "");

  if (!/^\d{6,8}$/.test(ci)) {
    return error("La cédula tiene que tener de 6 a 8 dígitos.");
  }

  let datos: AltaUsuario;

  if (usaContrasena(tipoAlta)) {
    const leida = leerContrasena(formData);
    if ("error" in leida) return error(leida.error);
    datos = { tipo: tipoAlta, ci, contrasena: leida.contrasena };
  } else {
    const leido = leerEmail(formData);
    if ("error" in leido) return error(leido.error);
    datos = { tipo: tipoAlta, ci, email: leido.email };
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

/**
 * Cambia el rol de la cédula ci. El formulario solo manda la contraseña o
 * el email cuando el cambio los pide; el backend decide igual.
 */
export async function cambiarRolAction(
  ci: string,
  _estadoAnterior: EstadoAltaUsuario,
  formData: FormData
): Promise<EstadoAltaUsuario> {
  const rol = tipoInterno(texto(formData, "rol"));

  if (!rol) {
    return error("Seleccioná un rol válido.");
  }

  const datos: CambioRol = { rol };

  if (formData.has("contrasena")) {
    const leida = leerContrasena(formData);
    if ("error" in leida) return error(leida.error);
    datos.contrasena = leida.contrasena;
  }

  if (formData.has("email")) {
    const leido = leerEmail(formData);
    if ("error" in leido) return error(leido.error);
    datos.email = leido.email;
  }

  const resultado = await cambiarRol(ci, datos);

  if (!resultado.ok) {
    return error(resultado.mensaje);
  }

  revalidatePath("/usuarios");

  return {
    estado: "exito",
    mensaje: `Rol de ${ci} cambiado correctamente.`,
  };
}

/**
 * Elimina a la cédula ci (sus roles de funcionario, administrador o
 * autoridad). Si sale bien la fila desaparece o pierde esos roles al
 * revalidar el listado.
 */
export async function eliminarUsuarioAction(
  ci: string,
  _estadoAnterior: EstadoAltaUsuario
): Promise<EstadoAltaUsuario> {
  const resultado = await eliminarUsuario(ci);

  if (!resultado.ok) {
    return error(resultado.mensaje);
  }

  revalidatePath("/usuarios");

  return {
    estado: "exito",
    mensaje: `Usuario ${ci} eliminado.`,
  };
}

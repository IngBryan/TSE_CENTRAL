
"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";

import {
  cookieWildFly,
  idSesion,
  NOMBRE_COOKIE_SESION,
  RUTA_COOKIE_SESION,
  urlBackend,
} from "@/lib/backoffice-sesion";

export type EstadoLogin = {
  estado: "inicial" | "error";
  mensaje: string;
};

export async function iniciarSesionAction(
  _anterior: EstadoLogin,
  formData: FormData,
): Promise<EstadoLogin> {
  const ci = formData.get("ci");
  const contrasena = formData.get("contrasena");

  if (
    typeof ci !== "string" ||
    !ci.trim() ||
    ci.length > 255 ||
    typeof contrasena !== "string" ||
    !contrasena
  ) {
    return {
      estado: "error",
      mensaje: "Ingresá tu cédula y contraseña.",
    };
  }

  let sesion: string;

  try {
    const respuesta = await fetch(urlBackend("auth/login"), {
      method: "POST",
      cache: "no-store",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify({
        ci: ci.trim(),
        contrasena,
      }),
    });

    if (respuesta.status === 401 || respuesta.status === 403) {
      return {
        estado: "error",
        mensaje: "Cédula o contraseña incorrectas.",
      };
    }

    if (!respuesta.ok) {
      return {
        estado: "error",
        mensaje: "No se pudo iniciar sesión.",
      };
    }

    const cookieBackend =
      respuesta.headers.get("set-cookie") ?? "";

    const encontrada = cookieBackend.match(
      /\bJSESSIONID=([A-Za-z0-9_.~-]+)/
    );

    if (!encontrada) {
      return {
        estado: "error",
        mensaje: "WildFly no devolvió una sesión válida.",
      };
    }

    sesion = encontrada[1];

    // Comprobar la identidad y el rol en WildFly.
    const comprobacion = await fetch(urlBackend("auth/me"), {
      cache: "no-store",
      headers: {
        Accept: "application/json",
        Cookie: cookieWildFly(sesion),
      },
    });

    if (!comprobacion.ok) {
      return {
        estado: "error",
        mensaje: "No se pudo validar la sesión.",
      };
    }

    const identidad: unknown = await comprobacion.json();

    if (
      !identidad ||
      typeof identidad !== "object" ||
      !("rol" in identidad) ||
      identidad.rol !== "ADMINISTRADOR"
    ) {
      await fetch(urlBackend("auth/logout"), {
        method: "POST",
        headers: {
          Cookie: cookieWildFly(sesion),
        },
        cache: "no-store",
      }).catch(() => undefined);

      return {
        estado: "error",
        mensaje: "Tu usuario no tiene rol Administrador.",
      };
    }
  } catch {
    return {
      estado: "error",
      mensaje: "No se pudo conectar con el servidor.",
    };
  }

  (await cookies()).set(NOMBRE_COOKIE_SESION, sesion, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: RUTA_COOKIE_SESION,
    maxAge: 30 * 60,
  });

  redirect("/nodos-perifericos");
}

export async function cerrarSesionAction(): Promise<void> {
  const sesion = await idSesion();

  if (sesion) {
    try {
      await fetch(urlBackend("auth/logout"), {
        method: "POST",
        cache: "no-store",
        headers: {
          Cookie: cookieWildFly(sesion),
        },
      });
    } catch {
      // Eliminar la cookie local aunque WildFly no responda.
    }
  }

  (await cookies()).delete({
    name: NOMBRE_COOKIE_SESION,
    path: RUTA_COOKIE_SESION,
  });

  redirect("/login");
}

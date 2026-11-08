
"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";

import {
  fetchBackend,
  idSesion,
  NOMBRE_COOKIE_SESION,
  RUTA_COOKIE_SESION,
  urlBackend,
  wildFlyAuthorization,
} from "@/lib/backoffice-sesion";
import { esRol, inicioPorRol, type Rol } from "@/lib/rutas";

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
  let duracionSesion: number;
  let rol: Rol;

  try {
    const respuesta = await fetchBackend(urlBackend("auth/login"), {
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

    // The login answers the token (JWT) and its lifetime in seconds
    const login: unknown = await respuesta.json();

    if (
      !login ||
      typeof login !== "object" ||
      !("token" in login) ||
      typeof login.token !== "string" ||
      !/^[A-Za-z0-9_.~-]+$/.test(login.token) ||
      !("expiraEn" in login) ||
      typeof login.expiraEn !== "number"
    ) {
      return {
        estado: "error",
        mensaje: "WildFly no devolvió una sesión válida.",
      };
    }

    sesion = login.token;
    duracionSesion = login.expiraEn;

    // Comprobar la identidad y el rol en WildFly.
    const comprobacion = await fetchBackend(urlBackend("auth/me"), {
      cache: "no-store",
      headers: {
        Accept: "application/json",
        Authorization: wildFlyAuthorization(sesion),
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
      !esRol(identidad.rol)
    ) {
      await fetchBackend(urlBackend("auth/logout"), {
        method: "POST",
        headers: {
          Authorization: wildFlyAuthorization(sesion),
        },
        cache: "no-store",
      }).catch(() => undefined);

      return {
        estado: "error",
        mensaje: "Tu usuario no tiene acceso al Backoffice.",
      };
    }

    rol = identidad.rol;
  } catch {
    return {
      estado: "error",
      mensaje: "No se pudo conectar con el servidor.",
    };
  }

  // The cookie lasts as long as the token: after that WildFly rejects it and the user logs in again
  (await cookies()).set(NOMBRE_COOKIE_SESION, sesion, {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
    path: RUTA_COOKIE_SESION,
    maxAge: duracionSesion,
  });

  redirect(inicioPorRol(rol));
}

export async function cerrarSesionAction(): Promise<void> {
  const sesion = await idSesion();

  if (sesion) {
    try {
      await fetchBackend(urlBackend("auth/logout"), {
        method: "POST",
        cache: "no-store",
        headers: {
          Authorization: wildFlyAuthorization(sesion),
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

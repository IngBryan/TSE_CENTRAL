
"use client";

import { useActionState } from "react";
import {
  iniciarSesionAction,
  type EstadoLogin,
} from "./actions";

const estadoInicial: EstadoLogin = {
  estado: "inicial",
  mensaje: "",
};

export default function FormularioLogin() {
  const [estado, accion, pendiente] = useActionState(
    iniciarSesionAction,
    estadoInicial
  );

  return (
    <form action={accion} className="space-y-5">
      <div className="space-y-2">
        <label
          htmlFor="ci"
          className="block text-sm font-medium"
        >
          Cédula de identidad
        </label>

        <input
          id="ci"
          name="ci"
          type="text"
          inputMode="numeric"
          autoComplete="username"
          maxLength={255}
          required
          autoFocus
          placeholder="Ingresá tu cédula"
          className="w-full rounded-md border border-input bg-background px-3 py-2"
        />
      </div>

      <div className="space-y-2">
        <label
          htmlFor="contrasena"
          className="block text-sm font-medium"
        >
          Contraseña
        </label>

        <input
          id="contrasena"
          name="contrasena"
          type="password"
          autoComplete="current-password"
          required
          placeholder="Ingresá tu contraseña"
          className="w-full rounded-md border border-input bg-background px-3 py-2"
        />
      </div>

      {estado.estado === "error" && (
        <p
          role="alert"
          className="rounded-md border border-destructive p-3 text-sm text-destructive"
        >
          {estado.mensaje}
        </p>
      )}

      <button
        type="submit"
        disabled={pendiente}
        className="w-full rounded-md bg-primary px-4 py-2 font-medium text-primary-foreground disabled:opacity-50"
      >
        {pendiente ? "Ingresando..." : "Iniciar sesión"}
      </button>
    </form>
  );
}

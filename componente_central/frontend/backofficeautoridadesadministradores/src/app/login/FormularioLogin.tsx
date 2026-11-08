"use client";

import { useActionState } from "react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { iniciarSesionAction, type EstadoLogin } from "./actions";

const estadoInicial: EstadoLogin = {
  estado: "inicial",
  mensaje: "",
};

export default function FormularioLogin() {
  const [estado, accion, pendiente] = useActionState(iniciarSesionAction, estadoInicial);

  return (
    <form action={accion} className="space-y-5">
      <div className="space-y-2">
        <Label htmlFor="ci">Cédula de identidad</Label>
        <Input
          id="ci"
          name="ci"
          type="text"
          inputMode="numeric"
          autoComplete="username"
          maxLength={255}
          required
          placeholder="Ingresá tu cédula"
          className="h-10"
        />
      </div>

      <div className="space-y-2">
        <Label htmlFor="contrasena">Contraseña</Label>
        <Input
          id="contrasena"
          name="contrasena"
          type="password"
          autoComplete="current-password"
          required
          placeholder="Ingresá tu contraseña"
          className="h-10"
        />
      </div>

      {estado.estado === "error" && (
        <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
          {estado.mensaje}
        </p>
      )}

      <Button type="submit" disabled={pendiente} className="h-10 w-full shadow-sm shadow-primary/30">
        {pendiente ? "Ingresando..." : "Iniciar sesión"}
      </Button>
    </form>
  );
}

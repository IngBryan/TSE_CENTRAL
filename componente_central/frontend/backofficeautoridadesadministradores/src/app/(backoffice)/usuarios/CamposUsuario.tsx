import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { ChangeEvent, ReactNode } from "react";

import {
  LARGO_MAXIMO_CONTRASENA,
  LARGO_MAXIMO_EMAIL,
  LARGO_MINIMO_CONTRASENA,
  type EstadoAltaUsuario,
} from "@/lib/usuarios";

// Campos compartidos por el alta y el cambio de rol (Input y Label de shadcn)


type AlCambiar = (e: ChangeEvent<HTMLInputElement>) => void;

type PropsCampo = {
  // Distingue los ids cuando hay un formulario por fila: "" o "-<ci>"
  sufijoId?: string;
  className?: string;
  value: string;
  onChange: AlCambiar;
};

function CampoContrasena({
  name,
  etiqueta,
  sufijoId = "",
  className = "",
  value,
  onChange,
  children,
}: PropsCampo & { name: string; etiqueta: string; children?: ReactNode }) {
  const id = `${name}${sufijoId}`;

  return (
    <div className={`space-y-2 ${className}`}>
      <Label htmlFor={id}>
        {etiqueta}
      </Label>

      <Input
        id={id}
        name={name}
        type="password"
        autoComplete="new-password"
        minLength={LARGO_MINIMO_CONTRASENA}
        maxLength={LARGO_MAXIMO_CONTRASENA}
        required
        value={value}
        onChange={onChange}
      />

      {children}
    </div>
  );
}

export function CamposContrasena({
  sufijoId,
  contrasena,
  confirmacion,
  onContrasena,
  onConfirmacion,
  claseContrasena,
  ayuda,
}: {
  sufijoId?: string;
  contrasena: string;
  confirmacion: string;
  onContrasena: AlCambiar;
  onConfirmacion: AlCambiar;
  claseContrasena?: string;
  ayuda?: ReactNode;
}) {
  return (
    <>
      <CampoContrasena
        name="contrasena"
        etiqueta="Contraseña inicial"
        sufijoId={sufijoId}
        className={claseContrasena}
        value={contrasena}
        onChange={onContrasena}
      >
        {ayuda}
      </CampoContrasena>

      <CampoContrasena
        name="confirmacion"
        etiqueta="Repetir contraseña"
        sufijoId={sufijoId}
        value={confirmacion}
        onChange={onConfirmacion}
      />
    </>
  );
}

export function CampoEmail({
  sufijoId = "",
  className = "",
  value,
  onChange,
}: PropsCampo) {
  const id = `email${sufijoId}`;

  return (
    <div className={`space-y-2 ${className}`}>
      <Label htmlFor={id}>
        Email
      </Label>

      <Input
        id={id}
        name="email"
        type="email"
        autoComplete="off"
        maxLength={LARGO_MAXIMO_EMAIL}
        placeholder="nombre@mtop.gub.uy"
        required
        value={value}
        onChange={onChange}
      />
    </div>
  );
}

export function MensajeEstado({
  estado,
  className = "",
}: {
  estado: EstadoAltaUsuario;
  className?: string;
}) {
  if (estado.estado === "inicial") {
    return null;
  }

  const esError = estado.estado === "error";

  return (
    <p
      role={esError ? "alert" : "status"}
      className={`text-sm ${
        esError ? "text-destructive" : "text-emerald-700"
      } ${className}`}
    >
      {estado.mensaje}
    </p>
  );
}

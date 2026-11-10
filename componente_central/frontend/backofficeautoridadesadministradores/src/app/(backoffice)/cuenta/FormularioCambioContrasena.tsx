"use client";

import { useActionState, useState, type ChangeEvent } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  LARGO_MAXIMO_CONTRASENA,
  LARGO_MINIMO_CONTRASENA,
  type EstadoAltaUsuario,
} from "@/lib/usuarios";
import { MensajeEstado } from "../usuarios/CamposUsuario";
import { cambiarContrasenaAction } from "./actions";

type Campos = { actual: string; nueva: string; confirmacion: string };

const camposVacios: Campos = { actual: "", nueva: "", confirmacion: "" };

const estadoInicial: EstadoAltaUsuario = { estado: "inicial", mensaje: "" };

const CAMPOS: {
  name: keyof Campos;
  etiqueta: string;
  autoComplete: "current-password" | "new-password";
}[] = [
  { name: "actual", etiqueta: "Contraseña actual", autoComplete: "current-password" },
  { name: "nueva", etiqueta: "Contraseña nueva", autoComplete: "new-password" },
  { name: "confirmacion", etiqueta: "Repetir contraseña nueva", autoComplete: "new-password" },
];

export default function FormularioCambioContrasena() {
  // Controlados: React reinicia el formulario despues de la accion y se perderia lo escrito
  const [campos, setCampos] = useState<Campos>(camposVacios);

  const [estado, accion, pendiente] = useActionState(
    async (anterior: EstadoAltaUsuario, formData: FormData): Promise<EstadoAltaUsuario> => {
      const resultado = await cambiarContrasenaAction(anterior, formData);

      // Las contraseñas no quedan en pantalla, salió bien o no
      setCampos(camposVacios);

      return resultado;
    },
    estadoInicial
  );

  const cambiar = (campo: keyof Campos) => (e: ChangeEvent<HTMLInputElement>) =>
    setCampos({ ...campos, [campo]: e.target.value });

  return (
    <Card className="max-w-lg shadow-sm">
      <CardHeader>
        <CardTitle className="text-lg font-semibold">Cambiar contraseña</CardTitle>
        <CardDescription>
          La nueva tiene que tener al menos {LARGO_MINIMO_CONTRASENA} caracteres
          y ser distinta de la actual.
        </CardDescription>
      </CardHeader>

      <CardContent>
        <form action={accion} className="space-y-4">
          {CAMPOS.map(({ name, etiqueta, autoComplete }) => (
            <div key={name} className="space-y-2">
              <Label htmlFor={name}>{etiqueta}</Label>
              <Input
                id={name}
                name={name}
                type="password"
                autoComplete={autoComplete}
                minLength={name === "actual" ? undefined : LARGO_MINIMO_CONTRASENA}
                maxLength={LARGO_MAXIMO_CONTRASENA}
                required
                value={campos[name]}
                onChange={cambiar(name)}
              />
            </div>
          ))}

          <Button type="submit" disabled={pendiente} className="h-9">
            {pendiente ? "Guardando..." : "Cambiar contraseña"}
          </Button>
        </form>

        <MensajeEstado estado={estado} className="mt-4" />
      </CardContent>
    </Card>
  );
}

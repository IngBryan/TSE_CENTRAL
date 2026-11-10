"use client";

import { useRouter } from "next/navigation";
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

const AYUDA = `La nueva tiene que tener al menos ${LARGO_MINIMO_CONTRASENA} caracteres y ser distinta de la actual.`;

/**
 * Cambio de la contraseña propia. obligatorio: el primer ingreso con la
 * contraseña que definió un administrador (la página /cambiar-contrasena, que
 * pone su propio marco); al cambiarla entra al backoffice.
 */
export default function FormularioCambioContrasena({ obligatorio = false }: { obligatorio?: boolean }) {
  const router = useRouter();

  // Controlados: React reinicia el formulario despues de la accion y se perderia lo escrito
  const [campos, setCampos] = useState<Campos>(camposVacios);

  const [estado, accion, pendiente] = useActionState(
    async (anterior: EstadoAltaUsuario, formData: FormData): Promise<EstadoAltaUsuario> => {
      const resultado = await cambiarContrasenaAction(anterior, formData);

      // Las contraseñas no quedan en pantalla, salió bien o no
      setCampos(camposVacios);

      // WildFly ya no lo bloquea, con la misma sesión: "/" lo lleva al inicio de su rol
      if (obligatorio && resultado.estado === "exito") {
        router.replace("/");
        router.refresh();
      }

      return resultado;
    },
    estadoInicial
  );

  const cambiar = (campo: keyof Campos) => (e: ChangeEvent<HTMLInputElement>) =>
    setCampos({ ...campos, [campo]: e.target.value });

  const formulario = (
    <>
      <form action={accion} className="space-y-4">
        {CAMPOS.map(({ name, etiqueta, autoComplete }) => (
          <div key={name} className="space-y-2">
            <Label htmlFor={name}>
              {obligatorio && name === "actual" ? "Contraseña que te dieron" : etiqueta}
            </Label>
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
              className={obligatorio ? "h-10" : undefined}
            />
          </div>
        ))}

        {obligatorio && <p className="text-xs text-muted-foreground">{AYUDA}</p>}

        <Button
          type="submit"
          disabled={pendiente}
          className={obligatorio ? "h-10 w-full shadow-sm shadow-primary/30" : "h-9"}
        >
          {pendiente ? "Guardando..." : obligatorio ? "Cambiar y continuar" : "Cambiar contraseña"}
        </Button>
      </form>

      {/* Al salir bien en el modo obligatorio se navega: el mensaje de éxito no se llega a ver */}
      <MensajeEstado estado={estado} className="mt-4" />
    </>
  );

  if (obligatorio) {
    return formulario;
  }

  return (
    <Card className="max-w-lg shadow-sm">
      <CardHeader>
        <CardTitle className="text-lg font-semibold">Cambiar contraseña</CardTitle>
        <CardDescription>{AYUDA}</CardDescription>
      </CardHeader>

      <CardContent>{formulario}</CardContent>
    </Card>
  );
}

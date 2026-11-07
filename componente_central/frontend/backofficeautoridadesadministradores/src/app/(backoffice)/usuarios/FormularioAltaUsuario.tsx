"use client";

import { useActionState, useState, type ChangeEvent } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { crearUsuarioAction } from "./actions";
import { CampoEmail, CamposContrasena, MensajeEstado } from "./CamposUsuario";
import {
  LARGO_MINIMO_CONTRASENA,
  NOMBRE_ROL,
  TIPOS_ALTA,
  usaContrasena,
  type EstadoAltaUsuario,
  type TipoAltaUsuario,
} from "@/lib/usuarios";

type CamposAlta = {
  tipo: TipoAltaUsuario;
  ci: string;
  contrasena: string;
  confirmacion: string;
  email: string;
};

const camposIniciales: CamposAlta = {
  tipo: "FUNCIONARIO",
  ci: "",
  contrasena: "",
  confirmacion: "",
  email: "",
};

const estadoInicial: EstadoAltaUsuario = {
  estado: "inicial",
  mensaje: "",
};

const DESCRIPCION_TIPO: Record<TipoAltaUsuario, string> = {
  ADMINISTRADOR:
    "Entra al backoffice con cédula y contraseña y gestiona nodos y usuarios.",
  AUTORIDAD:
    "Entra al backoffice con cédula y contraseña para consultar reportes.",
  FUNCIONARIO:
    "Funcionario del MTOP: entra al frontoffice de funcionarios con gub.uy.",
};

/**
 * Alta de un usuario: primero se elige el tipo y el formulario pide los
 * datos de ese tipo (contraseña para administrador y autoridad, email
 * para funcionario).
 */
export default function FormularioAltaUsuario() {
  // Controlados: React reinicia los formularios despues de cada accion y se perderian los datos ante un error
  const [campos, setCampos] = useState<CamposAlta>(camposIniciales);

  const [estado, accion, pendiente] = useActionState(
    async (
      anterior: EstadoAltaUsuario,
      formData: FormData
    ): Promise<EstadoAltaUsuario> => {
      const resultado = await crearUsuarioAction(anterior, formData);

      // Las contraseñas no quedan en pantalla, salió bien o no
      setCampos((actuales) =>
        resultado.estado === "exito"
          ? { ...camposIniciales, tipo: actuales.tipo }
          : { ...actuales, contrasena: "", confirmacion: "" }
      );

      return resultado;
    },
    estadoInicial
  );

  const conContrasena = usaContrasena(campos.tipo);

  const cambiar =
    (campo: keyof CamposAlta) =>
    (e: ChangeEvent<HTMLInputElement>) =>
      setCampos({ ...campos, [campo]: e.target.value });

  return (
    <Card className="shadow-sm">
      <CardHeader>
        <CardTitle className="text-lg font-semibold">Alta de usuario</CardTitle>
      </CardHeader>

      <CardContent>
        <form action={accion} className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2 sm:col-span-2">
            <Label htmlFor="tipo">Tipo de usuario</Label>

            {/* The name makes the select submit with the form */}
            <Select
              name="tipo"
              required
              items={Object.fromEntries(TIPOS_ALTA.map((tipo) => [tipo, NOMBRE_ROL[tipo]]))}
              value={campos.tipo}
              onValueChange={(tipo) => tipo && setCampos({ ...campos, tipo: tipo as TipoAltaUsuario })}
            >
              <SelectTrigger id="tipo" className="w-full sm:max-w-xs">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {TIPOS_ALTA.map((tipo) => (
                  <SelectItem key={tipo} value={tipo}>{NOMBRE_ROL[tipo]}</SelectItem>
                ))}
              </SelectContent>
            </Select>

            <p className="text-xs text-muted-foreground">
              {DESCRIPCION_TIPO[campos.tipo]} Los choferes y responsables de
              empresa se dan de alta desde el frontoffice.
            </p>
          </div>

          <div className="space-y-2">
            <Label htmlFor="ci">Cédula</Label>

            <Input
              id="ci"
              name="ci"
              type="text"
              inputMode="numeric"
              autoComplete="off"
              maxLength={11}
              placeholder="Ej. 1.234.567-8"
              required
              value={campos.ci}
              onChange={cambiar("ci")}
            />
          </div>

          {conContrasena ? (
            <CamposContrasena
              contrasena={campos.contrasena}
              confirmacion={campos.confirmacion}
              onContrasena={cambiar("contrasena")}
              onConfirmacion={cambiar("confirmacion")}
              claseContrasena="sm:col-start-1"
              ayuda={
                <p className="text-xs text-muted-foreground">
                  Al menos {LARGO_MINIMO_CONTRASENA} caracteres. Comunicásela a
                  la persona por un medio seguro.
                </p>
              }
            />
          ) : (
            <CampoEmail value={campos.email} onChange={cambiar("email")} />
          )}

          <div className="sm:col-span-2">
            <Button type="submit" disabled={pendiente} className="h-9">
              {pendiente ? "Creando..." : "Crear usuario"}
            </Button>
          </div>
        </form>

        <MensajeEstado estado={estado} className="mt-4" />
      </CardContent>
    </Card>
  );
}

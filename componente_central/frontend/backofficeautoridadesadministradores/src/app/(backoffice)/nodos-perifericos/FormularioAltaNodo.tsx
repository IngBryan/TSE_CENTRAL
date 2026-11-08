"use client";

import { useActionState, useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { EstadoAltaNodo, TipoNodo } from "@/lib/nodos-perifericos";
import { registrarNodoAction } from "./actions";

type CamposAlta = {
  tipo: TipoNodo;
  idExterno: string;
  nombre: string;
  ubicacion: string;
};

const camposIniciales: CamposAlta = {
  tipo: "BALANZA",
  idExterno: "",
  nombre: "",
  ubicacion: "",
};

const estadoInicial: EstadoAltaNodo = {
  estado: "inicial",
  mensaje: "",
};

const TIPOS: Record<TipoNodo, string> = { BALANZA: "Balanza", TRACKING: "Tracking" };

export default function FormularioAltaNodo() {
  const [campos, setCampos] = useState<CamposAlta>(camposIniciales);

  const [estado, accion, pendiente] = useActionState(
    async (anterior: EstadoAltaNodo, formData: FormData): Promise<EstadoAltaNodo> => {
      const resultado = await registrarNodoAction(anterior, formData);

      if (resultado.estado === "exito") {
        setCampos(camposIniciales);
      }

      return resultado;
    },
    estadoInicial
  );

  return (
    <Card className="shadow-sm">
      <CardHeader>
        <CardTitle className="text-lg font-semibold">Alta de nodo periférico</CardTitle>
      </CardHeader>

      <CardContent>
        <form action={accion} className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-2">
            <Label htmlFor="tipo">Tipo de nodo</Label>
            {/* The name makes the select submit with the form */}
            <Select
              name="tipo"
              required
              items={TIPOS}
              value={campos.tipo}
              onValueChange={(tipo) => tipo && setCampos({ ...campos, tipo: tipo as TipoNodo })}
            >
              <SelectTrigger id="tipo" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {(Object.keys(TIPOS) as TipoNodo[]).map((tipo) => (
                  <SelectItem key={tipo} value={tipo}>{TIPOS[tipo]}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="idExterno">Identificador externo</Label>
            <Input
              id="idExterno"
              name="idExterno"
              type="number"
              min="0"
              step="1"
              required
              value={campos.idExterno}
              onChange={(e) => setCampos({ ...campos, idExterno: e.target.value })}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="nombre">Nombre</Label>
            <Input
              id="nombre"
              name="nombre"
              maxLength={100}
              required
              value={campos.nombre}
              onChange={(e) => setCampos({ ...campos, nombre: e.target.value })}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="ubicacion">Ubicación</Label>
            <Input
              id="ubicacion"
              name="ubicacion"
              maxLength={200}
              required
              value={campos.ubicacion}
              onChange={(e) => setCampos({ ...campos, ubicacion: e.target.value })}
            />
          </div>

          <div className="sm:col-span-2">
            <Button type="submit" disabled={pendiente} className="h-9">
              {pendiente ? "Registrando..." : "Registrar nodo"}
            </Button>
          </div>
        </form>

        {estado.estado !== "inicial" && (
          <p
            role={estado.estado === "error" ? "alert" : "status"}
            className={`mt-4 text-sm ${estado.estado === "error" ? "text-destructive" : "text-emerald-700"}`}
          >
            {estado.mensaje}
          </p>
        )}
      </CardContent>
    </Card>
  );
}


"use client";

import { useActionState, useState } from "react";

import { registrarNodoAction } from "./actions";
import type {
  EstadoAltaNodo,
  TipoNodo,
} from "@/lib/nodos-perifericos";

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

const estilosCampo =
  "w-full rounded-md border border-input bg-background px-3 py-2 text-sm";

export default function FormularioAltaNodo() {
  const [campos, setCampos] =
    useState<CamposAlta>(camposIniciales);

  const [estado, accion, pendiente] = useActionState(
    async (
      anterior: EstadoAltaNodo,
      formData: FormData
    ): Promise<EstadoAltaNodo> => {
      const resultado = await registrarNodoAction(
        anterior,
        formData
      );

      if (resultado.estado === "exito") {
        setCampos(camposIniciales);
      }

      return resultado;
    },
    estadoInicial
  );

  return (
    <section className="rounded-lg border p-6">
      <h2 className="mb-5 text-xl font-semibold">
        Alta de nodo periférico
      </h2>

      <form
        action={accion}
        className="grid gap-4 sm:grid-cols-2"
      >
        <div className="space-y-2">
          <label
            htmlFor="tipo"
            className="text-sm font-medium"
          >
            Tipo de nodo
          </label>

          <select
            id="tipo"
            name="tipo"
            required
            value={campos.tipo}
            onChange={(e) =>
              setCampos({
                ...campos,
                tipo: e.target.value as TipoNodo,
              })
            }
            className={estilosCampo}
          >
            <option value="BALANZA">Balanza</option>
            <option value="TRACKING">Tracking</option>
          </select>
        </div>

        <div className="space-y-2">
          <label
            htmlFor="idExterno"
            className="text-sm font-medium"
          >
            Identificador externo
          </label>

          <input
            id="idExterno"
            name="idExterno"
            type="number"
            min="0"
            step="1"
            required
            value={campos.idExterno}
            onChange={(e) =>
              setCampos({
                ...campos,
                idExterno: e.target.value,
              })
            }
            className={estilosCampo}
          />
        </div>

        <div className="space-y-2">
          <label
            htmlFor="nombre"
            className="text-sm font-medium"
          >
            Nombre
          </label>

          <input
            id="nombre"
            name="nombre"
            type="text"
            maxLength={100}
            required
            value={campos.nombre}
            onChange={(e) =>
              setCampos({
                ...campos,
                nombre: e.target.value,
              })
            }
            className={estilosCampo}
          />
        </div>

        <div className="space-y-2">
          <label
            htmlFor="ubicacion"
            className="text-sm font-medium"
          >
            Ubicación
          </label>

          <input
            id="ubicacion"
            name="ubicacion"
            type="text"
            maxLength={200}
            required
            value={campos.ubicacion}
            onChange={(e) =>
              setCampos({
                ...campos,
                ubicacion: e.target.value,
              })
            }
            className={estilosCampo}
          />
        </div>

        <div className="sm:col-span-2">
          <button
            type="submit"
            disabled={pendiente}
            className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
          >
            {pendiente
              ? "Registrando..."
              : "Registrar nodo"}
          </button>
        </div>
      </form>

      {estado.estado !== "inicial" && (
        <p
          role={
            estado.estado === "error"
              ? "alert"
              : "status"
          }
          className={`mt-4 text-sm ${
            estado.estado === "error"
              ? "text-destructive"
              : "text-green-700 dark:text-green-400"
          }`}
        >
          {estado.mensaje}
        </p>
      )}
    </section>
  );
}

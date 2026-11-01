
"use client";

import { useState } from "react";

import type { TipoPermiso } from "@/lib/tipos-permiso-backend";
import FormularioTipoPermiso from "./FormularioTipoPermiso";

export default function FilaTipoPermiso({ tipo }: { tipo: TipoPermiso }) {
  const [editando, setEditando] = useState(false);

  if (editando) {
    return (
      <tr className="border-t">
        <td colSpan={5} className="p-3">
          <FormularioTipoPermiso
            tipo={tipo}
            alTerminar={() => setEditando(false)}
          />
        </td>
      </tr>
    );
  }

  return (
    <tr className="border-t">
      <td className="p-3">{tipo.nombre}</td>
      <td className="p-3">{tipo.pideNumero ? "Sí" : "No"}</td>
      <td className="p-3">{tipo.pideVencimiento ? "Sí" : "No"}</td>
      <td className="p-3">{tipo.activo ? "Activo" : "Inactivo"}</td>
      <td className="p-3 text-right">
        <button
          type="button"
          onClick={() => setEditando(true)}
          className="rounded-md border px-3 py-1 text-sm hover:bg-muted"
        >
          Modificar
        </button>
      </td>
    </tr>
  );
}

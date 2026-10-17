
"use client";

import { useState } from "react";
import FormularioAltaNodo from "./FormularioAltaNodo";

/**
 * Controla la visualización del formulario de alta
 * de nodos periféricos.
 */
export default function PanelAltaNodo() {
  const [abierto, setAbierto] = useState(false);

  return (
    <section className="space-y-4">
      <button
        type="button"
        onClick={() => setAbierto(!abierto)}
        aria-expanded={abierto}
        className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
      >
        {abierto ? "Cancelar" : "Agregar nodo"}
      </button>

      {abierto && <FormularioAltaNodo />}
    </section>
  );
}

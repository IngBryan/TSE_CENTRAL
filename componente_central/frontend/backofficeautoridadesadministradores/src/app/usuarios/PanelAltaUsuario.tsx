
"use client";

import { useState } from "react";
import FormularioAltaUsuario from "./FormularioAltaUsuario";

/**
 * Controla la visualización del formulario de alta de usuarios.
 */
export default function PanelAltaUsuario() {
  const [abierto, setAbierto] = useState(false);

  return (
    <section className="space-y-4">
      <button
        type="button"
        onClick={() => setAbierto(!abierto)}
        aria-expanded={abierto}
        className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground"
      >
        {abierto ? "Cancelar" : "Agregar usuario"}
      </button>

      {abierto && <FormularioAltaUsuario />}
    </section>
  );
}

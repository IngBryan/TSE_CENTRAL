"use client";

import { Plus, X } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import FormularioAltaUsuario from "./FormularioAltaUsuario";

/**
 * Controla la visualización del formulario de alta de usuarios.
 */
export default function PanelAltaUsuario() {
  const [abierto, setAbierto] = useState(false);

  return (
    <Collapsible open={abierto} onOpenChange={setAbierto} className="space-y-4">
      <CollapsibleTrigger render={<Button variant={abierto ? "outline" : "default"} className="h-9 gap-1.5" />}>
        {abierto ? <X /> : <Plus />}
        {abierto ? "Cancelar" : "Agregar usuario"}
      </CollapsibleTrigger>

      <CollapsibleContent>
        <FormularioAltaUsuario />
      </CollapsibleContent>
    </Collapsible>
  );
}

"use client";

import { Plus, X } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import FormularioAltaNodo from "./FormularioAltaNodo";

/**
 * Controla la visualización del formulario de alta
 * de nodos periféricos.
 */
export default function PanelAltaNodo() {
  const [abierto, setAbierto] = useState(false);

  return (
    <Collapsible open={abierto} onOpenChange={setAbierto} className="space-y-4">
      <CollapsibleTrigger render={<Button variant={abierto ? "outline" : "default"} className="h-9 gap-1.5" />}>
        {abierto ? <X /> : <Plus />}
        {abierto ? "Cancelar" : "Agregar nodo"}
      </CollapsibleTrigger>

      <CollapsibleContent>
        <FormularioAltaNodo />
      </CollapsibleContent>
    </Collapsible>
  );
}

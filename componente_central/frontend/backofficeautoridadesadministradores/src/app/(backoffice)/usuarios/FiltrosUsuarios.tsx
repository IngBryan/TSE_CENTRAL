import { Search } from "lucide-react";
import Form from "next/form";
import Link from "next/link";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  NOMBRE_ROL,
  type FiltrosUsuarios as Filtros,
  type RolUsuario,
} from "@/lib/usuarios";

// "" is every role: it travels as rol= and the page ignores it
const ROLES: Record<string, string> = {
  "": "Todos los roles",
  ...NOMBRE_ROL,
};

/**
 * Filtro por rol y buscador por cédula. Se envía por GET: los filtros
 * quedan en la URL y la búsqueda vuelve a la primera página.
 */
export default function FiltrosUsuarios({ filtros }: { filtros: Filtros }) {
  const hayFiltros = filtros.rol !== null || filtros.ci !== "";

  return (
    <Form
      action="/usuarios"
      role="search"
      className="flex flex-wrap items-end gap-3 rounded-xl border bg-card p-4 shadow-sm"
    >
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="filtro-ci">Cédula</Label>
        <Input
          id="filtro-ci"
          type="search"
          name="ci"
          defaultValue={filtros.ci}
          inputMode="numeric"
          maxLength={20}
          placeholder="Ej. 5407438"
          className="w-48"
        />
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="filtro-rol">Rol</Label>
        <Select name="rol" items={ROLES} defaultValue={filtros.rol ?? ""}>
          <SelectTrigger id="filtro-rol" className="w-56">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="">Todos los roles</SelectItem>
            {(Object.keys(NOMBRE_ROL) as RolUsuario[]).map((rol) => (
              <SelectItem key={rol} value={rol}>{NOMBRE_ROL[rol]}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <Button type="submit" className="h-9 gap-1.5">
        <Search /> Buscar
      </Button>

      {hayFiltros && (
        <Button variant="outline" className="h-9" nativeButton={false} render={<Link href="/usuarios" />}>
          Limpiar filtros
        </Button>
      )}
    </Form>
  );
}

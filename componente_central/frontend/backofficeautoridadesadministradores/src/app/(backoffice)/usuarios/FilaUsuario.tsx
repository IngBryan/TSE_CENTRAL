"use client";

import { useActionState, useState, type ChangeEvent } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { TableCell, TableRow } from "@/components/ui/table";
import { cambiarRolAction, eliminarUsuarioAction } from "./actions";
import { CampoEmail, CamposContrasena, MensajeEstado } from "./CamposUsuario";
import {
  NOMBRE_ROL,
  TIPOS_ALTA,
  cambioPideContrasena,
  cambioPideEmail,
  rolesInternos,
  type DatosUsuario,
  type EstadoAltaUsuario,
  type TipoAltaUsuario,
} from "@/lib/usuarios";

const COLUMNAS = 5;

type Props = {
  usuario: DatosUsuario;
  nombre: string;
  // La fila del administrador con sesión: no puede cambiar su propio rol
  esPropio: boolean;
};

/**
 * Una fila del listado y, si se abre, el formulario para cambiar su rol
 * (solo funcionarios, administradores y autoridades).
 */
export default function FilaUsuario({ usuario, nombre, esPropio }: Props) {
  const internos = rolesInternos(usuario.roles);
  const puedeCambiar = internos.length > 0 && !esPropio;
  // Qué panel está abierto debajo de la fila
  const [panel, setPanel] = useState<"rol" | "eliminar" | null>(null);

  const alternar = (cual: "rol" | "eliminar") =>
    setPanel(panel === cual ? null : cual);

  return (
    <>
      <TableRow>
        <TableCell className="px-3 font-mono">{usuario.ci}</TableCell>
        <TableCell className="px-3 font-medium">{nombre}</TableCell>
        <TableCell className="px-3 text-muted-foreground">{usuario.email ?? "—"}</TableCell>
        <TableCell className="px-3">
          <ul className="flex flex-wrap gap-1.5">
            {usuario.roles.map((rol) => (
              <li key={rol}>
                <Badge variant="secondary">{NOMBRE_ROL[rol] ?? rol}</Badge>
              </li>
            ))}
          </ul>
        </TableCell>
        <TableCell className="px-3">
          {puedeCambiar && (
            <div className="flex justify-end gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => alternar("rol")}
                aria-expanded={panel === "rol"}
              >
                {panel === "rol" ? "Cancelar" : "Cambiar rol"}
              </Button>

              <Button
                type="button"
                variant="destructive"
                size="sm"
                onClick={() => alternar("eliminar")}
                aria-expanded={panel === "eliminar"}
              >
                {panel === "eliminar" ? "Cancelar" : "Eliminar"}
              </Button>
            </div>
          )}
        </TableCell>
      </TableRow>

      {puedeCambiar && panel !== null && (
        <TableRow className="bg-muted/40 hover:bg-muted/40">
          <TableCell colSpan={COLUMNAS} className="p-4 whitespace-normal">
            {panel === "rol" ? (
              <FormularioCambioRol ci={usuario.ci} internos={internos} />
            ) : (
              <ConfirmacionBaja
                ci={usuario.ci}
                internos={internos}
                otrosRoles={usuario.roles.length > internos.length}
                onCancelar={() => setPanel(null)}
              />
            )}
          </TableCell>
        </TableRow>
      )}
    </>
  );
}

const estadoInicial: EstadoAltaUsuario = { estado: "inicial", mensaje: "" };

function ConfirmacionBaja({
  ci,
  internos,
  otrosRoles,
  onCancelar,
}: {
  ci: string;
  internos: TipoAltaUsuario[];
  // Chofer o responsable: no se tocan
  otrosRoles: boolean;
  onCancelar: () => void;
}) {
  const [estado, accion, pendiente] = useActionState(
    (anterior: EstadoAltaUsuario) => eliminarUsuarioAction(ci, anterior),
    estadoInicial
  );

  return (
    <form action={accion} className="space-y-3">
      <p className="text-sm">
        ¿Eliminar a <span className="font-mono">{ci}</span>? Se le quita el
        rol {internos.map((r) => NOMBRE_ROL[r]).join(" y ")}.
      </p>

      <ul className="list-disc space-y-1 pl-5 text-xs text-muted-foreground">
        {internos.includes("FUNCIONARIO") && (
          <li>
            Como funcionario queda inactivo: conserva su historial de casos
            pero ya no puede ingresar. Si se lo vuelve a dar de alta, se
            reactiva.
          </li>
        )}
        {internos.some((r) => r !== "FUNCIONARIO") && (
          <li>
            Se borra su usuario del backoffice, con su contraseña. Pierde el
            acceso en su siguiente pedido.
          </li>
        )}
        {otrosRoles && (
          <li>Sus roles de chofer o responsable de empresa no cambian.</li>
        )}
      </ul>

      <div className="flex gap-2">
        <Button
          type="submit"
          disabled={pendiente}
          className="h-9 bg-destructive text-white hover:bg-destructive/90"
        >
          {pendiente ? "Eliminando..." : "Sí, eliminar"}
        </Button>

        <Button type="button" variant="outline" className="h-9" onClick={onCancelar}>
          Cancelar
        </Button>
      </div>

      {estado.estado === "error" && (
        <p role="alert" className="text-sm text-destructive">
          {estado.mensaje}
        </p>
      )}
    </form>
  );
}

function FormularioCambioRol({
  ci,
  internos,
}: {
  ci: string;
  internos: TipoAltaUsuario[];
}) {
  // Si tiene un solo rol, ese no se ofrece: no sería un cambio
  const opciones = TIPOS_ALTA.filter(
    (tipo) => !(internos.length === 1 && internos[0] === tipo)
  );

  const [rolElegido, setRol] = useState<TipoAltaUsuario>(opciones[0]);
  // Tras un cambio exitoso la fila se revalida y el rol recién asignado deja
  // de ser una opción: se pasa a la primera, igual que el select
  const rol = opciones.includes(rolElegido) ? rolElegido : opciones[0];
  const [contrasena, setContrasena] = useState("");
  const [confirmacion, setConfirmacion] = useState("");
  const [email, setEmail] = useState("");

  const [estado, accion, pendiente] = useActionState(
    async (anterior: EstadoAltaUsuario, formData: FormData) => {
      const resultado = await cambiarRolAction(ci, anterior, formData);
      // Las contraseñas no quedan en pantalla
      setContrasena("");
      setConfirmacion("");
      return resultado;
    },
    estadoInicial
  );

  const pideContrasena = cambioPideContrasena(internos, rol);
  const pideEmail = cambioPideEmail(internos, rol);
  const quitados = internos.filter((r) => r !== rol);

  const valor =
    (cambiar: (v: string) => void) => (e: ChangeEvent<HTMLInputElement>) =>
      cambiar(e.target.value);

  return (
    <form action={accion} className="grid gap-4 sm:grid-cols-3">
      <div className="space-y-2">
        <Label htmlFor={`rol-${ci}`}>Rol nuevo</Label>

        {/* The name makes the select submit with the form */}
        <Select
          name="rol"
          items={Object.fromEntries(opciones.map((tipo) => [tipo, NOMBRE_ROL[tipo]]))}
          value={rol}
          onValueChange={(valor) => valor && setRol(valor as TipoAltaUsuario)}
        >
          <SelectTrigger id={`rol-${ci}`} className="w-full bg-card">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {opciones.map((tipo) => (
              <SelectItem key={tipo} value={tipo}>{NOMBRE_ROL[tipo]}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {pideContrasena && (
        <CamposContrasena
          sufijoId={`-${ci}`}
          contrasena={contrasena}
          confirmacion={confirmacion}
          onContrasena={valor(setContrasena)}
          onConfirmacion={valor(setConfirmacion)}
        />
      )}

      {pideEmail && (
        <CampoEmail
          sufijoId={`-${ci}`}
          className="sm:col-span-2"
          value={email}
          onChange={valor(setEmail)}
        />
      )}

      <div className="space-y-3 sm:col-span-3">
        {quitados.length > 0 && (
          <p className="text-xs text-muted-foreground">
            Se le quita el rol {quitados.map((r) => NOMBRE_ROL[r]).join(" y ")}.
            {quitados.includes("FUNCIONARIO") &&
              " Como funcionario queda inactivo: conserva su historial de casos pero ya no puede ingresar."}
            {pideContrasena &&
              " Comunicale la contraseña inicial por un medio seguro."}
          </p>
        )}

        <Button type="submit" disabled={pendiente} className="h-9">
          {pendiente ? "Guardando..." : "Cambiar rol"}
        </Button>

        <MensajeEstado estado={estado} />
      </div>
    </form>
  );
}

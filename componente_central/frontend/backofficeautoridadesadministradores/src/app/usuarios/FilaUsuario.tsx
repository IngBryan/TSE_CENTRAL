"use client";

import { useActionState, useState, type ChangeEvent } from "react";

import { cambiarRolAction, eliminarUsuarioAction } from "./actions";
import {
  LARGO_MAXIMO_CONTRASENA,
  LARGO_MINIMO_CONTRASENA,
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

const estilosCampo =
  "w-full rounded-md border border-input bg-background px-3 py-2 text-sm";

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
      <tr className="border-t">
        <td className="p-3 font-mono">{usuario.ci}</td>
        <td className="p-3">{nombre}</td>
        <td className="p-3">{usuario.email ?? "—"}</td>
        <td className="p-3">
          <ul className="flex flex-wrap gap-1.5">
            {usuario.roles.map((rol) => (
              <li
                key={rol}
                className="rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium"
              >
                {NOMBRE_ROL[rol] ?? rol}
              </li>
            ))}
          </ul>
        </td>
        <td className="p-3">
          {puedeCambiar && (
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => alternar("rol")}
                aria-expanded={panel === "rol"}
                className="whitespace-nowrap rounded-md border px-3 py-1.5 text-xs hover:bg-muted"
              >
                {panel === "rol" ? "Cancelar" : "Cambiar rol"}
              </button>

              <button
                type="button"
                onClick={() => alternar("eliminar")}
                aria-expanded={panel === "eliminar"}
                className="whitespace-nowrap rounded-md border border-destructive/40 px-3 py-1.5 text-xs text-destructive hover:bg-destructive/10"
              >
                {panel === "eliminar" ? "Cancelar" : "Eliminar"}
              </button>
            </div>
          )}
        </td>
      </tr>

      {puedeCambiar && panel !== null && (
        <tr className="bg-muted/40">
          <td colSpan={COLUMNAS} className="p-4">
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
          </td>
        </tr>
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
        <button
          type="submit"
          disabled={pendiente}
          className="rounded-md bg-destructive px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {pendiente ? "Eliminando..." : "Sí, eliminar"}
        </button>

        <button
          type="button"
          onClick={onCancelar}
          className="rounded-md border px-4 py-2 text-sm hover:bg-muted"
        >
          Cancelar
        </button>
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
        <label htmlFor={`rol-${ci}`} className="text-sm font-medium">
          Rol nuevo
        </label>

        <select
          id={`rol-${ci}`}
          name="rol"
          value={rol}
          onChange={(e) => setRol(e.target.value as TipoAltaUsuario)}
          className={estilosCampo}
        >
          {opciones.map((tipo) => (
            <option key={tipo} value={tipo}>
              {NOMBRE_ROL[tipo]}
            </option>
          ))}
        </select>
      </div>

      {pideContrasena && (
        <>
          <div className="space-y-2">
            <label htmlFor={`contrasena-${ci}`} className="text-sm font-medium">
              Contraseña inicial
            </label>
            <input
              id={`contrasena-${ci}`}
              name="contrasena"
              type="password"
              autoComplete="new-password"
              minLength={LARGO_MINIMO_CONTRASENA}
              maxLength={LARGO_MAXIMO_CONTRASENA}
              required
              value={contrasena}
              onChange={valor(setContrasena)}
              className={estilosCampo}
            />
          </div>

          <div className="space-y-2">
            <label htmlFor={`confirmacion-${ci}`} className="text-sm font-medium">
              Repetir contraseña
            </label>
            <input
              id={`confirmacion-${ci}`}
              name="confirmacion"
              type="password"
              autoComplete="new-password"
              minLength={LARGO_MINIMO_CONTRASENA}
              maxLength={LARGO_MAXIMO_CONTRASENA}
              required
              value={confirmacion}
              onChange={valor(setConfirmacion)}
              className={estilosCampo}
            />
          </div>
        </>
      )}

      {pideEmail && (
        <div className="space-y-2 sm:col-span-2">
          <label htmlFor={`email-${ci}`} className="text-sm font-medium">
            Email
          </label>
          <input
            id={`email-${ci}`}
            name="email"
            type="email"
            autoComplete="off"
            maxLength={254}
            placeholder="nombre@mtop.gub.uy"
            required
            value={email}
            onChange={valor(setEmail)}
            className={estilosCampo}
          />
        </div>
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

        <button
          type="submit"
          disabled={pendiente}
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
        >
          {pendiente ? "Guardando..." : "Cambiar rol"}
        </button>

        {estado.estado !== "inicial" && (
          <p
            role={estado.estado === "error" ? "alert" : "status"}
            className={`text-sm ${
              estado.estado === "error"
                ? "text-destructive"
                : "text-green-700 dark:text-green-400"
            }`}
          >
            {estado.mensaje}
          </p>
        )}
      </div>
    </form>
  );
}

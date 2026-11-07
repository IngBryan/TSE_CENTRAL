"use client";

import { useActionState, useState, type ChangeEvent } from "react";

import { crearUsuarioAction } from "./actions";
import {
  LARGO_MAXIMO_CONTRASENA,
  LARGO_MINIMO_CONTRASENA,
  NOMBRE_ROL,
  TIPOS_ALTA,
  usaContrasena,
  type EstadoAltaUsuario,
  type TipoAltaUsuario,
} from "@/lib/usuarios";

type CamposAlta = {
  tipo: TipoAltaUsuario;
  ci: string;
  contrasena: string;
  confirmacion: string;
  email: string;
};

const camposIniciales: CamposAlta = {
  tipo: "FUNCIONARIO",
  ci: "",
  contrasena: "",
  confirmacion: "",
  email: "",
};

const estadoInicial: EstadoAltaUsuario = {
  estado: "inicial",
  mensaje: "",
};

const DESCRIPCION_TIPO: Record<TipoAltaUsuario, string> = {
  ADMINISTRADOR:
    "Entra al backoffice con cédula y contraseña y gestiona nodos y usuarios.",
  AUTORIDAD:
    "Entra al backoffice con cédula y contraseña para consultar reportes.",
  FUNCIONARIO:
    "Funcionario del MTOP: entra al frontoffice de funcionarios con gub.uy.",
};

const estilosCampo =
  "w-full rounded-md border border-input bg-background px-3 py-2 text-sm";

/**
 * Alta de un usuario: primero se elige el tipo y el formulario pide los
 * datos de ese tipo (contraseña para administrador y autoridad, email
 * para funcionario).
 */
export default function FormularioAltaUsuario() {
  // Controlados: React reinicia los formularios despues de cada accion y se perderian los datos ante un error
  const [campos, setCampos] = useState<CamposAlta>(camposIniciales);

  const [estado, accion, pendiente] = useActionState(
    async (
      anterior: EstadoAltaUsuario,
      formData: FormData
    ): Promise<EstadoAltaUsuario> => {
      const resultado = await crearUsuarioAction(anterior, formData);

      // Las contraseñas no quedan en pantalla, salió bien o no
      setCampos((actuales) =>
        resultado.estado === "exito"
          ? { ...camposIniciales, tipo: actuales.tipo }
          : { ...actuales, contrasena: "", confirmacion: "" }
      );

      return resultado;
    },
    estadoInicial
  );

  const conContrasena = usaContrasena(campos.tipo);

  const cambiar =
    (campo: keyof CamposAlta) =>
    (e: ChangeEvent<HTMLInputElement>) =>
      setCampos({ ...campos, [campo]: e.target.value });

  return (
    <section className="rounded-lg border p-6">
      <h2 className="mb-5 text-xl font-semibold">Alta de usuario</h2>

      <form action={accion} className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2 sm:col-span-2">
          <label htmlFor="tipo" className="text-sm font-medium">
            Tipo de usuario
          </label>

          <select
            id="tipo"
            name="tipo"
            required
            value={campos.tipo}
            onChange={(e) =>
              setCampos({
                ...campos,
                tipo: e.target.value as TipoAltaUsuario,
              })
            }
            className={`${estilosCampo} sm:max-w-xs`}
          >
            {TIPOS_ALTA.map((tipo) => (
              <option key={tipo} value={tipo}>
                {NOMBRE_ROL[tipo]}
              </option>
            ))}
          </select>

          <p className="text-xs text-muted-foreground">
            {DESCRIPCION_TIPO[campos.tipo]} Los choferes y responsables de
            empresa se dan de alta desde el frontoffice.
          </p>
        </div>

        <div className="space-y-2">
          <label htmlFor="ci" className="text-sm font-medium">
            Cédula
          </label>

          <input
            id="ci"
            name="ci"
            type="text"
            inputMode="numeric"
            autoComplete="off"
            maxLength={11}
            placeholder="Ej. 1.234.567-8"
            required
            value={campos.ci}
            onChange={cambiar("ci")}
            className={estilosCampo}
          />
        </div>

        {conContrasena ? (
          <>
            <div className="space-y-2 sm:col-start-1">
              <label htmlFor="contrasena" className="text-sm font-medium">
                Contraseña inicial
              </label>

              <input
                id="contrasena"
                name="contrasena"
                type="password"
                autoComplete="new-password"
                minLength={LARGO_MINIMO_CONTRASENA}
                maxLength={LARGO_MAXIMO_CONTRASENA}
                required
                value={campos.contrasena}
                onChange={cambiar("contrasena")}
                className={estilosCampo}
              />

              <p className="text-xs text-muted-foreground">
                Al menos {LARGO_MINIMO_CONTRASENA} caracteres. Comunicásela a
                la persona por un medio seguro.
              </p>
            </div>

            <div className="space-y-2">
              <label htmlFor="confirmacion" className="text-sm font-medium">
                Repetir contraseña
              </label>

              <input
                id="confirmacion"
                name="confirmacion"
                type="password"
                autoComplete="new-password"
                minLength={LARGO_MINIMO_CONTRASENA}
                maxLength={LARGO_MAXIMO_CONTRASENA}
                required
                value={campos.confirmacion}
                onChange={cambiar("confirmacion")}
                className={estilosCampo}
              />
            </div>
          </>
        ) : (
          <div className="space-y-2">
            <label htmlFor="email" className="text-sm font-medium">
              Email
            </label>

            <input
              id="email"
              name="email"
              type="email"
              autoComplete="off"
              maxLength={254}
              placeholder="nombre@mtop.gub.uy"
              required
              value={campos.email}
              onChange={cambiar("email")}
              className={estilosCampo}
            />
          </div>
        )}

        <div className="sm:col-span-2">
          <button
            type="submit"
            disabled={pendiente}
            className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
          >
            {pendiente ? "Creando..." : "Crear usuario"}
          </button>
        </div>
      </form>

      {estado.estado !== "inicial" && (
        <p
          role={estado.estado === "error" ? "alert" : "status"}
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

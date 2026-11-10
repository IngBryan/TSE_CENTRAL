export type Rol = "ADMINISTRADOR" | "AUTORIDAD";

// Handled entirely by proxy.ts: drops the session cookie and goes to the login
export const RUTA_CERRAR_SESION = "/sesion/cerrar";

// La cuenta del usuario logueado (cambio de contraseña); la tienen todos los roles
export const RUTA_CUENTA = "/cuenta";

// Mientras no cambie la contraseña inicial que le dio un administrador, WildFly solo le deja cambiarla: todas
// las páginas mandan acá (fuera del layout del backoffice, sin el menú)
export const RUTA_CAMBIO_OBLIGATORIO = "/cambiar-contrasena";

// Each protected section and the roles that may open it; proxy.ts and the pages read this same table
export const RUTAS_PROTEGIDAS: readonly { prefijo: string; roles: readonly Rol[] }[] = [
  { prefijo: "/nodos-perifericos", roles: ["ADMINISTRADOR"] },
  { prefijo: "/reglas-fiscalizacion", roles: ["ADMINISTRADOR"] },
  { prefijo: "/usuarios", roles: ["ADMINISTRADOR"] },
  { prefijo: "/dashboard", roles: ["AUTORIDAD"] },
  { prefijo: "/reportes", roles: ["AUTORIDAD"] },
  { prefijo: RUTA_CUENTA, roles: ["ADMINISTRADOR", "AUTORIDAD"] },
  { prefijo: RUTA_CAMBIO_OBLIGATORIO, roles: ["ADMINISTRADOR", "AUTORIDAD"] },
];

const INICIO_POR_ROL: Record<Rol, string> = {
  ADMINISTRADOR: "/nodos-perifericos",
  AUTORIDAD: "/dashboard",
};

export function esRol(valor: unknown): valor is Rol {
  return valor === "ADMINISTRADOR" || valor === "AUTORIDAD";
}

export function inicioPorRol(rol: Rol): string {
  return INICIO_POR_ROL[rol];
}

// Adónde va un usuario logueado: primero el cambio de contraseña obligatorio, después el inicio de su rol
export function destinoTrasLogin(identidad: { rol: Rol; debeCambiarContrasena: boolean }): string {
  return identidad.debeCambiarContrasena ? RUTA_CAMBIO_OBLIGATORIO : inicioPorRol(identidad.rol);
}

export function rolesPermitidos(ruta: string): readonly Rol[] | null {
  const protegida = RUTAS_PROTEGIDAS.find(
    ({ prefijo }) => ruta === prefijo || ruta.startsWith(`${prefijo}/`)
  );

  return protegida ? protegida.roles : null;
}

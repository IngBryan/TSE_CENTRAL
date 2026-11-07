
export type RolUsuario =
  | "ADMINISTRADOR"
  | "AUTORIDAD"
  | "FUNCIONARIO"
  | "RESPONSABLE"
  | "CHOFER";

// El backend omite los campos sin valor (no manda null)
export type DatosUsuario = {
  ci: string;
  nombres?: string | null;
  apellidos?: string | null;
  email?: string | null;
  roles: RolUsuario[];
};

export type PaginaUsuarios = {
  usuarios: DatosUsuario[];
  pagina: number;
  tamanio: number;
  total: number;
  totalPaginas: number;
};

// Los que se crean desde el backoffice. Choferes y responsables tienen su propio alta (CU-02 y la gestion
// de empresas del frontoffice de funcionarios)
export type TipoAltaUsuario = "ADMINISTRADOR" | "AUTORIDAD" | "FUNCIONARIO";

export const TIPOS_ALTA: TipoAltaUsuario[] = ["ADMINISTRADOR", "AUTORIDAD", "FUNCIONARIO"];

// Las mismas reglas que valida el backend (AltaUsuarioService)
export const LARGO_MINIMO_CONTRASENA = 12;
export const LARGO_MAXIMO_CONTRASENA = 128;
export const LARGO_MAXIMO_EMAIL = 254;

export function usaContrasena(tipo: TipoAltaUsuario): boolean {
  return tipo !== "FUNCIONARIO";
}

export type AltaUsuario = {
  tipo: TipoAltaUsuario;
  ci: string;
  contrasena?: string;
  email?: string;
};

export type EstadoAltaUsuario = {
  estado: "inicial" | "exito" | "error";
  mensaje: string;
};

// Cambio de rol (las mismas reglas que CambioRolService): el rol nuevo reemplaza a los anteriores
export function rolesInternos(roles: RolUsuario[]): TipoAltaUsuario[] {
  return TIPOS_ALTA.filter((tipo) => roles.includes(tipo));
}

// Pasar a administrador o autoridad sin serlo pide una contraseña inicial
export function cambioPideContrasena(
  internos: TipoAltaUsuario[],
  nuevo: TipoAltaUsuario
): boolean {
  return usaContrasena(nuevo) && !internos.some(usaContrasena);
}

// Pasar a funcionario sin serlo pide el email
export function cambioPideEmail(
  internos: TipoAltaUsuario[],
  nuevo: TipoAltaUsuario
): boolean {
  return nuevo === "FUNCIONARIO" && !internos.includes("FUNCIONARIO");
}

export type CambioRol = {
  rol: TipoAltaUsuario;
  contrasena?: string;
  email?: string;
};

export type FiltrosUsuarios = {
  rol: RolUsuario | null;
  ci: string;
};

export function esRolUsuario(valor: unknown): valor is RolUsuario {
  // hasOwn y no `in`: `in` también acepta "toString", "constructor", etc.
  return typeof valor === "string" && Object.hasOwn(NOMBRE_ROL, valor);
}

export const NOMBRE_ROL: Record<RolUsuario, string> = {
  ADMINISTRADOR: "Administrador",
  AUTORIDAD: "Autoridad",
  FUNCIONARIO: "Funcionario",
  RESPONSABLE: "Responsable de empresa",
  CHOFER: "Chofer",
};

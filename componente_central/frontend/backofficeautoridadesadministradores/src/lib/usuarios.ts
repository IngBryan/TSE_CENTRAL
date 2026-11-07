
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

export type FiltrosUsuarios = {
  rol: RolUsuario | null;
  ci: string;
};

export function esRolUsuario(valor: unknown): valor is RolUsuario {
  return typeof valor === "string" && valor in NOMBRE_ROL;
}

export const NOMBRE_ROL: Record<RolUsuario, string> = {
  ADMINISTRADOR: "Administrador",
  AUTORIDAD: "Autoridad",
  FUNCIONARIO: "Funcionario",
  RESPONSABLE: "Responsable de empresa",
  CHOFER: "Chofer",
};

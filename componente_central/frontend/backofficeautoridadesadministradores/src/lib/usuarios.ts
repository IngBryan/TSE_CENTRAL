
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

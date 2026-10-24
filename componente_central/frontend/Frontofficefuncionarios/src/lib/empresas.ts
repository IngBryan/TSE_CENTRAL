// Tipos de la API /empresas del backend Frontofficefuncionarios (records de GestionEmpresasService).

// Fila del listado (GET /empresas)
export type EmpresaResumen = {
  nroEmpresa: number;
  nombrePublico: string | null;
  razonSocial: string | null;
  habilitada: boolean;
  ciResponsable: string | null;
};

// Solo primer nombre y primer apellido del responsable (Ley 18.331); estado frente a la PDI
export type ResponsableVista = {
  ci: string;
  primerNombre: string | null;
  primerApellido: string | null;
  estado: string | null;
};

export type EmpresaDetalle = {
  nroEmpresa: number;
  nombrePublico: string | null;
  razonSocial: string | null;
  direccion: string | null;
  habilitada: boolean;
  responsable: ResponsableVista | null;
};

// Cuerpo de PUT /empresas/{nro}: el numero de empresa no se modifica
export type CambiosEmpresa = {
  nombrePublico: string;
  razonSocial: string;
  direccion: string;
  habilitada: boolean;
};

// Cuerpo de POST /empresas. ciResponsable: cedula de un usuario disponible, o null para darla de alta sin
// responsable.
export type AltaEmpresa = {
  nroEmpresa: number;
  nombrePublico: string;
  razonSocial: string;
  direccion: string;
  habilitada: boolean;
  ciResponsable: string | null;
};

// GET /empresas/usuarios-disponibles: validados que no son responsables de ninguna empresa
export type UsuarioDisponible = { ci: string; primerNombre: string | null; primerApellido: string | null };

export const nombreUsuario = (u: { primerNombre: string | null; primerApellido: string | null }) =>
  [u.primerNombre, u.primerApellido].filter(Boolean).join(" ") || "Sin nombre registrado";

export type OperacionRealizada = { mensaje: string; empresa: EmpresaDetalle };

// GestionEmpresasService.MAX_NOMBRE y MAX_DIRECCION
export const MAX_NOMBRE = 100;
export const MAX_DIRECCION = 200;

export const ESTADOS_VALIDACION: Record<string, string> = {
  PENDIENTE_VALIDACION: "Pendiente de validación",
  VALIDADO: "Validado",
  RECHAZADO: "Rechazado",
  NO_HABILITADO: "No habilitado",
};

export const COLOR_HABILITADA = {
  si: "bg-emerald-100 text-emerald-900 dark:bg-emerald-950 dark:text-emerald-200",
  no: "bg-zinc-100 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-200",
};

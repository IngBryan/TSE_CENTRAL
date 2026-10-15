// Tipos de la API /casos del backend Frontofficefuncionarios (records de GestionCasosService) y
// textos para mostrarlos.

export type EstadoCaso = "DETECTADA" | "NOTIFICADA" | "EN_DESCARGO" | "CONFIRMADA" | "DESESTIMADA";
export type Decision = "CONFIRMAR" | "DESESTIMAR";

export type GuiaVista = {
  id: string;
  fecha: string | null; // LocalDate: 2026-10-13
  origen: string | null;
  destino: string | null;
  rubroCliente: string | null;
  tipoCarga: string | null;
  pesoCargaKg: number;
  estado: string | null;
};

// Fila de la bandeja (GET /casos)
export type CasoResumen = {
  id: number;
  tipoCaso: string;
  descripcion: string | null;
  estado: EstadoCaso | null;
  fechaDeteccion: string | null;
  idGuia: string;
  nroEmpresa: number | null;
  empresa: string | null;
  matricula: string | null;
  tieneDescargo: boolean;
  puedeResolverse: boolean;
};

// GET /casos: pagina empieza en 1; totalPaginas es 0 si no hay casos
export type PaginaCasos = { casos: CasoResumen[]; pagina: number; tamanio: number; total: number; totalPaginas: number };

export type ResolucionVista = { decision: Decision; fundamento: string; ciFuncionario: string; fechaHora: string | null };

export type RegistroVista = {
  fechaHora: string | null;
  usuario: string;
  accion: "CREACION" | "CAMBIO_ESTADO" | "RESOLUCION";
  estadoAnterior: EstadoCaso | null;
  estadoNuevo: EstadoCaso;
  fundamento: string | null;
};

export type CasoDetalle = {
  id: number;
  tipoCaso: string;
  descripcion: string | null;
  estado: EstadoCaso | null;
  fechaDeteccion: string | null;
  nroEmpresa: number | null;
  empresa: string | null;
  guia: GuiaVista;
  matricula: string | null;
  tieneDescargo: boolean;
  puedeResolverse: boolean;
  estadosSiguientes: EstadoCaso[];
  resolucion: ResolucionVista | null;
  historial: RegistroVista[];
};

export type VehiculoVista = {
  matricula: string;
  marca: string | null;
  modelo: string | null;
  pesoKg: number;
  capacidadCargaKg: number;
  pesoMaximoKg: number;
  nroPermiso: string | null;
  vencimientoPermiso: string | null;
  vencimientoItv: string | null;
};

// excesoKg: lo que el pesaje supera al peso del vehiculo mas su capacidad declarada (0 si no supera)
export type PesajeVista = { fechaHora: string | null; balanza: string | null; pesoKg: number; excesoKg: number };

export type EvidenciaVista = {
  idCaso: number;
  tipoCaso: string;
  descripcion: string | null;
  fechaDeteccion: string | null;
  guia: GuiaVista;
  vehiculo: VehiculoVista | null;
  pesajes: PesajeVista[];
  idsEventosTracking: string[];
};

export type DescargoVista = { idCaso: number; id: number; descripcion: string | null; estado: string | null };

export type ResolucionRegistrada = { mensaje: string; caso: CasoDetalle };

// Cuerpo de los errores del backend (400 / 403 / 404 / 409)
export type ErrorBackend = { error?: string; detalle?: string; campos?: Record<string, string> };

export const ESTADOS: Record<EstadoCaso, string> = {
  DETECTADA: "Detectada",
  NOTIFICADA: "Notificada",
  EN_DESCARGO: "En descargo",
  CONFIRMADA: "Confirmada",
  DESESTIMADA: "Desestimada",
};

export const COLOR_ESTADO: Record<EstadoCaso, string> = {
  DETECTADA: "bg-zinc-100 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-200",
  NOTIFICADA: "bg-sky-100 text-sky-900 dark:bg-sky-950 dark:text-sky-200",
  EN_DESCARGO: "bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-200",
  CONFIRMADA: "bg-red-100 text-red-900 dark:bg-red-950 dark:text-red-200",
  DESESTIMADA: "bg-emerald-100 text-emerald-900 dark:bg-emerald-950 dark:text-emerald-200",
};

// Reglas de fiscalizacion de la letra (seccion 3.5). Un tipo desconocido se muestra legible igual.
const TIPOS_CASO: Record<string, string> = {
  EXCESO_CARGA: "Exceso de carga",
  EXCESO_DE_CARGA: "Exceso de carga",
  SIN_HABILITACION: "Vehículo sin habilitación vigente",
  VEHICULO_SIN_HABILITACION: "Vehículo sin habilitación vigente",
  SIN_GUIA: "Viaje sin guía",
  VIAJE_SIN_GUIA: "Viaje sin guía",
  DESVIO_RUTA: "Desvío de ruta",
  DESVIO_DE_RUTA: "Desvío de ruta",
};

export function tipoCaso(tipo: string): string {
  if (TIPOS_CASO[tipo]) return TIPOS_CASO[tipo];
  const texto = tipo.replace(/_/g, " ").toLowerCase();
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

export const ACCIONES: Record<RegistroVista["accion"], string> = {
  CREACION: "Caso detectado",
  CAMBIO_ESTADO: "Cambio de estado",
  RESOLUCION: "Resolución del funcionario",
};

export const DECISIONES: Record<Decision, string> = {
  CONFIRMAR: "Confirmado",
  DESESTIMAR: "Desestimado",
};

export const estado = (e: string | null | undefined) => (e ? (ESTADOS[e as EstadoCaso] ?? e) : "-");

// Instant (ISO con Z) a fecha y hora de Uruguay
export const fechaHora = (iso: string | null | undefined) =>
  iso
    ? new Date(iso).toLocaleString("es-UY", { timeZone: "America/Montevideo", dateStyle: "short", timeStyle: "short" })
    : "-";

// LocalDate (2026-10-13) a 13/10/2026
export const fecha = (d: string | null | undefined) => (d ? d.split("-").reverse().join("/") : "-");

export const kg = (n: number) => `${n.toLocaleString("es-UY")} kg`;

// Tipos de la API /empresas/{nro}/vehiculos del backend Frontofficefuncionarios (records de
// FiscalizacionHabilitacionesService). El backend no manda los campos null: por eso son opcionales.

export type EstadoDocumento = "VIGENTE" | "POR_VENCER" | "VENCIDO" | "AUN_NO_VIGENTE" | "SIN_REGISTRO";
export type EstadoVehiculo = "HABILITADO" | "NO_HABILITADO" | "DADO_DE_BAJA";

// Periodo de validez de un permiso nacional de circulacion o de una ITV. nro: solo el permiso.
// diasParaVencer: de hoy al vencimiento, negativo si ya vencio. Fechas yyyy-MM-dd.
export type PeriodoVista = {
  nro?: string;
  desde?: string;
  hasta?: string;
  estado: EstadoDocumento;
  diasParaVencer?: number;
};

export type VehiculoFiscalizado = {
  matricula: string;
  marca?: string;
  modelo?: string;
  estado: EstadoVehiculo;
  permiso: PeriodoVista;
  // La de vencimiento mas lejano
  itv: PeriodoVista;
};

// porVencer: habilitados con el permiso o la ITV por vencer
export type ResumenHabilitaciones = {
  total: number;
  habilitados: number;
  noHabilitados: number;
  dadosDeBaja: number;
  porVencer: number;
};

// GET /empresas/{nro}/vehiculos. vehiculos: primero los no habilitados, despues los habilitados y al final
// los dados de baja. fecha: el dia contra el que se calculo la vigencia.
export type FiscalizacionEmpresa = {
  nroEmpresa: number;
  nombrePublico?: string;
  habilitada: boolean;
  fecha: string;
  resumen: ResumenHabilitaciones;
  vehiculos: VehiculoFiscalizado[];
};

// GET /empresas/{nro}/vehiculos/{matricula}: itvs es el historial, la de vencimiento mas lejano primero
export type VehiculoDetalle = {
  nroEmpresa: number;
  fecha: string;
  vehiculo: VehiculoFiscalizado;
  itvs: PeriodoVista[];
};

export const ESTADOS_DOCUMENTO: Record<EstadoDocumento, string> = {
  VIGENTE: "Vigente",
  POR_VENCER: "Por vencer",
  VENCIDO: "Vencido",
  AUN_NO_VIGENTE: "Aún no vigente",
  SIN_REGISTRO: "Sin registro",
};

export const ESTADOS_VEHICULO: Record<EstadoVehiculo, string> = {
  HABILITADO: "Habilitado",
  NO_HABILITADO: "No habilitado",
  DADO_DE_BAJA: "Dado de baja",
};

// Color del punto que acompana cada estado. Se destacan el verde (al dia), el ambar (por vencer) y el rojo
// (documento vencido); el resto va en gris y el texto dice que falta.
const VERDE = "bg-emerald-500";
const AMBAR = "bg-amber-500";
const ROJO = "bg-red-500";
const GRIS = "bg-zinc-400 dark:bg-zinc-500";

export const PUNTO_DOCUMENTO: Record<EstadoDocumento, string> = {
  VIGENTE: VERDE,
  POR_VENCER: AMBAR,
  VENCIDO: ROJO,
  AUN_NO_VIGENTE: GRIS,
  SIN_REGISTRO: GRIS,
};

export const PUNTO_VEHICULO: Record<EstadoVehiculo, string> = {
  HABILITADO: VERDE,
  NO_HABILITADO: GRIS,
  // Hueco: ya no circula
  DADO_DE_BAJA: "border border-zinc-400 dark:border-zinc-500",
};

// Una linea con el estado de un permiso o una ITV: "Vigente", "Vence en 12 días", "Venció hace 5 días"...
export function estadoPeriodo(p: PeriodoVista): string {
  switch (p.estado) {
    case "VIGENTE":
      return ESTADOS_DOCUMENTO.VIGENTE;
    case "POR_VENCER":
    case "VENCIDO":
      return plazo(p.diasParaVencer) ?? ESTADOS_DOCUMENTO[p.estado];
    case "AUN_NO_VIGENTE":
      return `Desde el ${fecha(p.desde)}`;
    case "SIN_REGISTRO":
      return ESTADOS_DOCUMENTO.SIN_REGISTRO;
  }
}

// Filtros del listado de vehiculos de una empresa (?estado=)
export const FILTROS = {
  todos: "Todos",
  no_habilitados: "No habilitados",
  por_vencer: "Por vencer",
  de_baja: "Dados de baja",
} as const;
export type Filtro = keyof typeof FILTROS;

export function esFiltro(valor: unknown): valor is Filtro {
  return typeof valor === "string" && Object.hasOwn(FILTROS, valor);
}

export function cumpleFiltro(v: VehiculoFiscalizado, filtro: Filtro): boolean {
  switch (filtro) {
    case "todos":
      return true;
    case "no_habilitados":
      return v.estado === "NO_HABILITADO";
    case "por_vencer":
      return v.estado === "HABILITADO" && (v.permiso.estado === "POR_VENCER" || v.itv.estado === "POR_VENCER");
    case "de_baja":
      return v.estado === "DADO_DE_BAJA";
  }
}

// "2026-11-02" -> "02/11/2026", sin pasar por Date (que lo correria de dia por la zona horaria)
export function fecha(iso: string | undefined): string {
  if (!iso) return "-";
  const [a, m, d] = iso.split("-");
  return `${d}/${m}/${a}`;
}

// "Vence en 12 días", "Vence hoy", "Venció hace 3 días"
export function plazo(dias: number | undefined): string | null {
  if (dias === undefined) return null;
  if (dias === 0) return "Vence hoy";
  if (dias === 1) return "Vence mañana";
  if (dias > 0) return `Vence en ${dias} días`;
  if (dias === -1) return "Venció ayer";
  return `Venció hace ${-dias} días`;
}

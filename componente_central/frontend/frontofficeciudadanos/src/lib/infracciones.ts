// Gestion de Infracciones: lo que devuelve el backend (/empresa/infracciones) y como se muestra.

export type EstadoInfraccion = "NOTIFICADA" | "EN_DESCARGO" | "CONFIRMADA" | "DESESTIMADA";

export type InfraccionResumen = {
  id: number;
  tipoCaso: string;
  descripcion: string | null;
  estado: EstadoInfraccion;
  fechaDeteccion: string;
  matricula: string | null;
  nueva: boolean;
  venceDescargo: string | null; // solo mientras esta NOTIFICADA
  admiteDescargo: boolean;
};

export type InfraccionDetalle = {
  infraccion: InfraccionResumen;
  guia: {
    id: string;
    fecha: string | null;
    origen: string | null;
    destino: string | null;
    rubroCliente: string | null;
    pesoCargaKg: number;
  } | null;
  vehiculo: {
    matricula: string;
    marca: string;
    modelo: string;
    pesoKg: number;
    capacidadCargaKg: number;
    pesoMaximoKg: number;
  } | null;
  pesajes: { fechaHora: string | null; balanza: string | null; pesoKg: number; excesoKg: number }[];
  eventosTracking: number;
  descargo: { descripcion: string; fechaPresentacion: string | null; ciPresentante: string | null } | null;
  resolucion: { decision: "CONFIRMADA" | "DESESTIMADA"; fundamento: string | null; fechaHora: string | null } | null;
  plazoDescargoDias: number;
};

export const MAX_DESCARGO = 2000;

export const TIPOS: Record<string, string> = {
  EXCESO_CARGA: "Exceso de carga",
  VEHICULO_SIN_HABILITACION: "Vehículo sin habilitación vigente",
  VIAJE_SIN_GUIA: "Viaje sin guía",
  DESVIO_RUTA: "Desvío de ruta",
};

export const tipoInfraccion = (t: string) => TIPOS[t] ?? t.replace(/_/g, " ").toLowerCase();

export const ESTADOS: Record<EstadoInfraccion, { texto: string; color: string }> = {
  NOTIFICADA: { texto: "Notificada", color: "bg-sky-100 text-sky-900 dark:bg-sky-950 dark:text-sky-200" },
  EN_DESCARGO: {
    texto: "En descargo",
    color: "bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-200",
  },
  CONFIRMADA: { texto: "Confirmada", color: "bg-red-100 text-red-900 dark:bg-red-950 dark:text-red-200" },
  DESESTIMADA: {
    texto: "Desestimada",
    color: "bg-emerald-100 text-emerald-900 dark:bg-emerald-950 dark:text-emerald-200",
  },
};

// Instant del backend (2026-10-27T21:32:00Z) en hora de Uruguay
export const fechaHora = (iso: string | null | undefined) =>
  iso
    ? new Date(iso).toLocaleString("es-UY", { timeZone: "America/Montevideo", dateStyle: "short", timeStyle: "short" })
    : "-";

// LocalDate (2026-10-13) a 13/10/2026
export const fecha = (d: string | null | undefined) => (d ? d.split("-").reverse().join("/") : "-");

// Notificaciones de casos (campana del encabezado): /empresa/infracciones/notificaciones
export type Notificacion = {
  id: number;
  tipoCaso: string;
  matricula: string | null;
  estado: EstadoInfraccion;
  fecha: string | null; // cuando se le notifico el caso a la empresa
  vista: boolean;
  admiteDescargo: boolean;
  venceDescargo: string | null;
};

export type Notificaciones = { noVistas: number; notificaciones: Notificacion[] };

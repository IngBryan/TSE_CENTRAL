// Tipos de la API /viajes del backend Frontofficefuncionarios (records de MonitoreoViajesService) y
// textos para mostrarlos. Fechas y kg se formatean con las funciones de casos.ts.

// Solo se muestran los viajes que el chofer ya inicio (MonitoreoViajesService.VISIBLES)
export type EstadoViaje = "EN_CURSO" | "FINALIZADA";

// Solo primer nombre y primer apellido del chofer (Ley 18.331)
export type ChoferVista = { ci: string | null; primerNombre: string | null; primerApellido: string | null };

// Fila del listado (GET /viajes)
export type ViajeResumen = {
  id: string;
  fecha: string | null; // LocalDate: 2026-10-13
  origen: string | null;
  destino: string | null;
  estado: EstadoViaje;
  nroEmpresa: number | null;
  empresa: string | null;
  matricula: string | null;
  chofer: ChoferVista | null;
};

// GET /viajes: pagina empieza en 1; totalPaginas es 0 si no hay viajes
export type PaginaViajes = { viajes: ViajeResumen[]; pagina: number; tamanio: number; total: number; totalPaginas: number };

export type VehiculoVista = {
  matricula: string;
  marca: string | null;
  modelo: string | null;
  pesoKg: number;
  capacidadCargaKg: number;
  pesoMaximoKg: number;
};

export type TipoEvento = "INICIO" | "FIN" | "CARGA_PARCIAL" | "DESCARGA_PARCIAL" | "INCIDENTE";

// Evento registrado por el chofer desde la app movil. resultado: que hizo el central con el evento.
export type EventoVista = {
  idEvento: string;
  tipo: TipoEvento;
  fechaHoraDispositivo: string | null;
  fechaRecepcion: string | null;
  chofer: ChoferVista | null;
  descripcion: string | null;
  latitud: number | null;
  longitud: number | null;
  conFoto: boolean;
  resultado: "APLICADO" | "SIN_EFECTO" | "CONFLICTO";
  codigo: string | null;
  detalle: string | null;
};

// Cambio hecho por el responsable de la empresa antes de que empezara el viaje
export type ModificacionVista = {
  tipo: "EDICION" | "REASIGNACION" | "CANCELACION";
  fecha: string | null;
  ciResponsable: string;
  cambios: string | null;
  motivo: string | null;
};

// excesoKg: lo que el pesaje supera al peso del vehiculo mas su capacidad declarada (0 si no supera)
export type PesajeVista = { fechaHora: string | null; balanza: string | null; pesoKg: number; excesoKg: number };

// inicio y fin: hora del INICIO y del FIN aplicados (null si todavia no ocurrieron). pesajes: los del
// vehiculo entre inicio y fin, o hasta ahora si el viaje sigue en curso.
export type ViajeDetalle = {
  id: string;
  fecha: string | null;
  origen: string | null;
  destino: string | null;
  rubroCliente: string | null;
  tipoCarga: string | null;
  pesoCargaKg: number;
  estado: EstadoViaje;
  fechaAsignacion: string | null;
  nroEmpresa: number | null;
  empresa: string | null;
  vehiculo: VehiculoVista | null;
  chofer: ChoferVista | null;
  inicio: string | null;
  fin: string | null;
  eventos: EventoVista[];
  modificaciones: ModificacionVista[];
  pesajes: PesajeVista[];
};

export const ESTADOS_VIAJE: Record<EstadoViaje, string> = {
  EN_CURSO: "En curso",
  FINALIZADA: "Finalizado",
};

export const COLOR_ESTADO_VIAJE: Record<EstadoViaje, string> = {
  EN_CURSO: "bg-sky-100 text-sky-900 dark:bg-sky-950 dark:text-sky-200",
  FINALIZADA: "bg-zinc-100 text-zinc-800 dark:bg-zinc-800 dark:text-zinc-200",
};

export const TIPOS_EVENTO: Record<TipoEvento, string> = {
  INICIO: "Inicio del viaje",
  FIN: "Fin del viaje",
  CARGA_PARCIAL: "Carga parcial",
  DESCARGA_PARCIAL: "Descarga parcial",
  INCIDENTE: "Incidente",
};

export const MODIFICACIONES: Record<ModificacionVista["tipo"], string> = {
  EDICION: "La empresa editó la guía",
  REASIGNACION: "La empresa reasignó la guía",
  CANCELACION: "La empresa canceló la guía",
};

export const estadoViaje = (e: string | null | undefined) => (e ? (ESTADOS_VIAJE[e as EstadoViaje] ?? e) : "-");

export const nombreChofer = (c: ChoferVista | null | undefined) =>
  c ? [c.primerNombre, c.primerApellido].filter(Boolean).join(" ") || `C.I. ${c.ci ?? "-"}` : "-";

// Ids de guia como los genera Frontofficeciudadanos (GV-3F9A12C4); cualquier otra cosa es 404 sin ir al backend
export const idValido = (id: string) => /^[A-Za-z0-9_-]{1,64}$/.test(id);

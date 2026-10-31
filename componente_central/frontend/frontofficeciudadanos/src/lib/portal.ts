// Portal publico (sin login): lo que devuelve el backend en /publico.

export type EmpresaEncontrada = { nroEmpresa: number; nombrePublico: string; razonSocial: string; habilitada: boolean };

export type ValidacionVehiculo = {
  matricula: string | null;
  deLaEmpresa: boolean;
  activo: boolean | null;
  permisoVence: string | null;
  permisoVigente: boolean | null;
  itvVence: string | null;
  itvVigente: boolean | null;
  habilitado: boolean;
};

export type ValidacionEmpresa = {
  nroEmpresa: number;
  nombrePublico: string;
  razonSocial: string;
  habilitada: boolean;
  vehiculo: ValidacionVehiculo | null;
  consultadoEl: string;
};

export type Valor = { valor: number | null; reservado: boolean };

// Indicador con decimales: valor null y reservado si no alcanza el umbral; null sin reservar si no hay datos
export type Indicador = { valor: number | null; reservado: boolean };

// cantidad y kg en null si la fila esta reservada por el umbral de agregacion
export type FilaPublica = { clave: string; etiqueta: string; cantidad: number | null; kg: number | null; reservado: boolean };

export type Estadisticas = {
  anio: number;
  aniosDisponibles: number[];
  umbralEmpresas: number;
  generadas: string;
  empresasHabilitadas: Valor;
  vehiculosHabilitados: Valor;
  viajes: Valor;
  kgTransportados: Valor;
  viajesPorMes: FilaPublica[];
  cargaPorRubro: FilaPublica[];
  infraccionesConfirmadasPorTipo: FilaPublica[];
  infraccionesCada100Viajes: Indicador;
  casosResueltos: Valor;
  porcentajeDesestimados: Indicador;
  medianaDiasResolucion: Indicador;
};

export const numero = (n: number | null | undefined) => (n == null ? "-" : n.toLocaleString("es-UY"));

// kg a toneladas con un decimal
export const toneladas = (kg: number | null | undefined) =>
  kg == null ? "-" : `${(kg / 1000).toLocaleString("es-UY", { maximumFractionDigits: 1 })} t`;

// Un decimal, con coma
export const decimal = (n: number | null | undefined) =>
  n == null ? "-" : n.toLocaleString("es-UY", { minimumFractionDigits: 1, maximumFractionDigits: 1 });

// Tablas que se pueden descargar en CSV (/estadisticas/csv?tabla=...)
export const TABLAS = {
  meses: "viajesPorMes",
  rubros: "cargaPorRubro",
  infracciones: "infraccionesConfirmadasPorTipo",
} as const;
export type Tabla = keyof typeof TABLAS;

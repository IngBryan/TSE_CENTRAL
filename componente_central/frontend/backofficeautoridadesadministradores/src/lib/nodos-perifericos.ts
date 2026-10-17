
export type TipoNodo = "BALANZA" | "TRACKING";

export type DatosNodo = {
  id: number;
  idExterno: number;
  nombre: string;
  tipo: TipoNodo;
  ubicacion: string;
  activo: boolean;
  ultimaComunicacionExitosa: string | null;
};

export type AltaNodo = {
  tipo: TipoNodo;
  nombre: string;
  ubicacion: string;
  idExterno: number;
};

export type EstadoAltaNodo = {
  estado: "inicial" | "exito" | "error";
  mensaje: string;
};

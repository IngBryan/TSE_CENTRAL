export type EstadoDocumentoHabilitacion = "VIGENTE" | "VENCIDO" | "AUN_NO_VIGENTE" | "SIN_REGISTRO";

export type PruebaHabilitacion = {
  fechaHora: string;
  origen: "TRACKING" | "PESAJE";
  nroPermiso: string | null;
  emisionPermiso: string | null;
  vencimientoPermiso: string | null;
  estadoPermiso: EstadoDocumentoHabilitacion;
  emisionItv: string | null;
  vencimientoItv: string | null;
  estadoItv: EstadoDocumentoHabilitacion;
};

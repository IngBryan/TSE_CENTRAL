export type ConfiguracionReglas = {
  revision: number;
  toleranciaCargaPorcentaje: number;
  toleranciaCargaKg: number;
  distanciaDesvioMetros: number;
  ventanaDesvioSegundos: number;
  fechaActualizacion: string;
  usuario: string;
  fundamento: string;
  publicada: boolean;
};

export type CambioReglas = Pick<ConfiguracionReglas,
  "toleranciaCargaPorcentaje" | "toleranciaCargaKg" | "distanciaDesvioMetros" | "ventanaDesvioSegundos" | "fundamento"
> & { revisionBase: number };

export type ResultadoReglas =
  | { ok: true; configuracion: ConfiguracionReglas }
  | { ok: false; mensaje: string; conflicto?: boolean };

export type EstadoReglas = {
  estado: "inicial" | "error" | "exito";
  mensaje?: string;
  conflicto?: boolean;
  configuracion?: ConfiguracionReglas;
};

export function esConfiguracion(valor: unknown): valor is ConfiguracionReglas {
  if (!valor || typeof valor !== "object") return false;
  const c = valor as Record<string, unknown>;
  return ["revision", "toleranciaCargaPorcentaje", "toleranciaCargaKg", "distanciaDesvioMetros", "ventanaDesvioSegundos"]
    .every(k => typeof c[k] === "number" && Number.isFinite(c[k]))
    && ["fechaActualizacion", "usuario", "fundamento"].every(k => typeof c[k] === "string")
    && typeof c.publicada === "boolean";
}

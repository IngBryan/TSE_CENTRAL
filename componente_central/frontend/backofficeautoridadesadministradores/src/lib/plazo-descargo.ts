export type PlazoDescargo = {
  revision: number;
  plazoDescargoDias: number;
  fechaActualizacion: string;
  usuario: string;
  fundamento: string;
};
export type CambioPlazoDescargo = Pick<PlazoDescargo, "plazoDescargoDias" | "fundamento"> & { revisionBase: number };
export type ResultadoPlazoDescargo =
  | { ok: true; configuracion: PlazoDescargo }
  | { ok: false; mensaje: string; conflicto?: boolean };
export type EstadoPlazoDescargo = {
  estado: "inicial" | "error" | "exito";
  mensaje?: string;
  conflicto?: boolean;
  configuracion?: PlazoDescargo;
};
export function esPlazoDescargo(valor: unknown): valor is PlazoDescargo {
  if (!valor || typeof valor !== "object") return false;
  const c = valor as Record<string, unknown>;
  return typeof c.revision === "number" && Number.isSafeInteger(c.revision) && c.revision >= 1
    && typeof c.plazoDescargoDias === "number" && Number.isInteger(c.plazoDescargoDias)
    && c.plazoDescargoDias >= 1 && c.plazoDescargoDias <= 365
    && ["fechaActualizacion", "usuario", "fundamento"].every(k => typeof c[k] === "string");
}

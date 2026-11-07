/** Contratos existentes de Frontoffice Ciudadanos para catálogo, OSRM y rutas de Guía. */
export type { LocalidadVista } from "@/lib/localidades";
export type RutaVista = {
  alternativa: number;
  sha256: string;
  distanciaMetros: number;
  duracionSegundos: number;
  geoJson: string;
};
export type Previsualizacion = {
  origenId: number;
  destinoId: number;
  previsualizacionId: string;
  rutas: RutaVista[];
};
export type RutaAsignada = {
  idRuta: number;
  alternativa: number;
  distanciaMetros: number;
  geometriaGeoJson: string;
};
export type RespuestaRutas<T> = { ok: true; datos: T } | { ok: false; error: string };

export { normalizarLocalidad } from "@/lib/localidades";

/** Contratos existentes de Frontoffice Ciudadanos para catálogo, OSRM y rutas de Guía. */
export type LocalidadVista = { id: number; nombre: string; departamento: string };
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

export const normalizarLocalidad = (valor: string) => valor.normalize("NFD")
  .replace(/[\u0300-\u036f]/g, "").trim().replace(/\s+/g, " ").toUpperCase();

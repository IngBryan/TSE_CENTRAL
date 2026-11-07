// Contratos de la API de seguimiento de Frontoffice Funcionarios (tarea 3B).
// Los datos de Tracking vienen de MongoDB, no de los eventos del móvil.
export type PuntoGpsSeguimiento = {
  messageId: string;
  gpsId: number;
  matricula: string;
  latitud: number;
  longitud: number;
  fechaHora: string;
};

export type PaginaGps = {
  idGuia: string;
  matricula: string;
  desde: string;
  hasta: string;
  posiciones: PuntoGpsSeguimiento[];
  siguienteDesde: string | null;
  hayMas: boolean;
};

export type UltimaGps = {
  idGuia: string;
  matricula: string;
  posicion: PuntoGpsSeguimiento | null;
};

export type RutaAutorizada = {
  idRuta: number;
  alternativa: number;
  sha256: string;
  distanciaMetros: number;
  geometriaGeoJson: string; // El backend devuelve un String, NO un objeto GeoJSON.
};

export type Coordenada = [longitud: number, latitud: number];

const coordenadaValida = (valor: unknown): valor is Coordenada =>
  Array.isArray(valor) && valor.length >= 2 &&
  typeof valor[0] === "number" && Number.isFinite(valor[0]) && Math.abs(valor[0]) <= 180 &&
  typeof valor[1] === "number" && Number.isFinite(valor[1]) && Math.abs(valor[1]) <= 90;

/** Convierte la geometría OSRM canónica en coordenadas para MapLibre. Ignora geometrías inválidas. */
export function coordenadasRuta(ruta: RutaAutorizada): Coordenada[] | null {
  try {
    const geojson: unknown = JSON.parse(ruta.geometriaGeoJson);
    if (!geojson || typeof geojson !== "object" || !("type" in geojson)
      || geojson.type !== "LineString" || !("coordinates" in geojson)
      || !Array.isArray(geojson.coordinates)) return null;
    const coords: unknown[] = geojson.coordinates;
    return coords.length >= 2 && coords.every(coordenadaValida)
      ? coords.map((c): Coordenada => [c[0], c[1]])
      : null;
  } catch {
    return null;
  }
}

export const posicionValida = (p: PuntoGpsSeguimiento): boolean =>
  coordenadaValida([p.longitud, p.latitud]) && Number.isFinite(Date.parse(p.fechaHora));

/** El mismo evento puede venir en dos páginas/actualizaciones; se muestra una sola vez. */
export function unirPosiciones(
  anteriores: PuntoGpsSeguimiento[], nuevas: PuntoGpsSeguimiento[],
): PuntoGpsSeguimiento[] {
  const eventos = new Map<string, PuntoGpsSeguimiento>();
  for (const p of [...anteriores, ...nuevas]) {
    if (!posicionValida(p)) continue;
    eventos.set(p.messageId || `${p.matricula}:${p.fechaHora}`, p);
  }
  return [...eventos.values()].sort((a, b) =>
    Date.parse(a.fechaHora) - Date.parse(b.fechaHora) || a.messageId.localeCompare(b.messageId));
}

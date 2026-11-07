/** Identidad de la localidad seleccionada; el nombre es solo para presentacion. */
export type LocalidadVista = { id: number; nombre: string; departamento: string };

export const normalizarLocalidad = (valor: string) => valor.normalize("NFD")
  .replace(/[\u0300-\u036f]/g, "").trim().replace(/\s+/g, " ").toUpperCase();

/** En guias anteriores al catalogo, solo inferimos el ID cuando el nombre no es ambiguo. */
export function localidadDeGuia(
  localidades: LocalidadVista[], id: number | null | undefined, nombre: string,
): LocalidadVista | undefined {
  if (id != null) return localidades.find(l => l.id === id);
  const candidatas = localidades.filter(l => normalizarLocalidad(l.nombre) === normalizarLocalidad(nombre));
  return candidatas.length === 1 ? candidatas[0] : undefined;
}

export function mostrarLocalidad(nombre: string, departamento?: string | null): string {
  return departamento ? `${nombre} (${departamento})` : nombre;
}

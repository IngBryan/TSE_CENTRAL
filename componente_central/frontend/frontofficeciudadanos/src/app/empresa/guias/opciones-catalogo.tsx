import type { ItemCatalogo } from "./actions";

// Opciones de un select de catalogo (rubro del cliente, tipo de carga): una primera vacia para obligar a
// elegir y los valores del catalogo. Si el valor actual no esta en el catalogo (dato viejo, o el catalogo no
// se pudo obtener) se agrega igual, para no perderlo al editar.
export function OpcionesCatalogo({ items, vacio, actual }: { items: ItemCatalogo[]; vacio: string; actual?: string }) {
  return (
    <>
      <option value="" disabled>
        {vacio}
      </option>
      {items.map((t) => (
        <option key={t.id} value={t.nombre}>
          {t.nombre}
        </option>
      ))}
      {actual && !items.some((t) => t.nombre === actual) && <option value={actual}>{actual}</option>}
    </>
  );
}

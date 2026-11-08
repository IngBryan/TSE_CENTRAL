import { fetchBackend, idSesion, urlBackend, wildFlyAuthorization } from "./backoffice-sesion";

// Contract of GET dashboard/facets/{facetId}/values in the backoffice API
export type ValorFaceta = { id: string; name: string };

type ResultadoValores = { ok: true; valores: ValorFaceta[] } | { ok: false; mensaje: string };

function esValor(valor: unknown): valor is ValorFaceta {
  return (
    valor !== null &&
    typeof valor === "object" &&
    "id" in valor &&
    typeof valor.id === "string" &&
    "name" in valor &&
    typeof valor.name === "string"
  );
}

// Every value of a facet, with no filter: what a filter lists
export async function valoresFaceta(facetId: string): Promise<ResultadoValores> {
  const sesion = await idSesion();
  if (!sesion) return { ok: false, mensaje: "La sesión venció. Vuelve a iniciar sesión." };

  try {
    const respuesta = await fetchBackend(urlBackend(`dashboard/facets/${encodeURIComponent(facetId)}/values`), {
      headers: { Accept: "application/json", Authorization: wildFlyAuthorization(sesion) },
      cache: "no-store",
    });

    if (!respuesta.ok) return { ok: false, mensaje: "No se pudieron cargar los valores del filtro." };

    const datos: unknown = await respuesta.json();
    const valores = datos !== null && typeof datos === "object" && "values" in datos ? datos.values : null;

    return Array.isArray(valores) && valores.every(esValor)
      ? { ok: true, valores }
      : { ok: false, mensaje: "La respuesta del backend no es válida." };
  } catch {
    return { ok: false, mensaje: "No se pudo establecer conexión con el backend." };
  }
}

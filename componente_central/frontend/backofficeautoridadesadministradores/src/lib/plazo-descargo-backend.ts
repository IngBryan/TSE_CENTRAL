import { fetchBackend, idSesion, urlBackend, wildFlyAuthorization } from "./backoffice-sesion";
import { esPlazoDescargo, type CambioPlazoDescargo, type ResultadoPlazoDescargo } from "./plazo-descargo";

async function consultar(ruta: string, cambio?: CambioPlazoDescargo): Promise<Response | null> {
  const token = await idSesion();
  if (!token) return null;
  return fetchBackend(urlBackend(ruta), {
    method: cambio ? "PUT" : "GET",
    cache: "no-store",
    headers: { Authorization: wildFlyAuthorization(token), Accept: "application/json", "Content-Type": "application/json" },
    ...(cambio ? { body: JSON.stringify(cambio) } : {}),
  });
}

export async function obtenerPlazoDescargo(cambio?: CambioPlazoDescargo): Promise<ResultadoPlazoDescargo> {
  try {
    const respuesta = await consultar("plazo-descargo", cambio);
    if (!respuesta || respuesta.status === 401) return { ok: false, mensaje: "La sesión venció. Volvé a iniciar sesión." };
    if (respuesta.status === 403) return { ok: false, mensaje: "Solo los administradores pueden configurar el plazo de descargo." };
    const datos: unknown = await respuesta.json();
    if (!respuesta.ok) {
      const detalle = datos && typeof datos === "object" && "detalle" in datos && typeof datos.detalle === "string"
        ? datos.detalle : "No se pudo obtener la configuración del plazo de descargo.";
      return { ok: false, mensaje: detalle, conflicto: respuesta.status === 409 };
    }
    return esPlazoDescargo(datos) ? { ok: true, configuracion: datos }
      : { ok: false, mensaje: "La configuración recibida no es válida." };
  } catch {
    return { ok: false, mensaje: "No se pudo conectar con el servicio de configuración." };
  }
}

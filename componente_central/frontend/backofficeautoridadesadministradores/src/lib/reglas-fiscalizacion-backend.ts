import { fetchBackend, idSesion, urlBackend, wildFlyAuthorization } from "./backoffice-sesion";
import { esConfiguracion, type CambioReglas, type ResultadoReglas, type ConfiguracionReglas } from "./reglas-fiscalizacion";

async function consultar(ruta: string, cambio?: CambioReglas): Promise<Response | null> {
  const token = await idSesion();
  if (!token) return null;
  return fetchBackend(urlBackend(ruta), {
    method: cambio ? "PUT" : "GET",
    cache: "no-store",
    headers: { Authorization: wildFlyAuthorization(token), Accept: "application/json", "Content-Type": "application/json" },
    ...(cambio ? { body: JSON.stringify(cambio) } : {}),
  });
}

export async function obtenerReglas(cambio?: CambioReglas): Promise<ResultadoReglas> {
  try {
    const respuesta = await consultar("reglas-fiscalizacion", cambio);
    if (!respuesta || respuesta.status === 401) return { ok: false, mensaje: "La sesión venció. Volvé a iniciar sesión." };
    if (respuesta.status === 403) return { ok: false, mensaje: "Solo los administradores pueden configurar reglas." };
    const datos: unknown = await respuesta.json();
    if (!respuesta.ok) {
      const detalle = datos && typeof datos === "object" && "detalle" in datos && typeof datos.detalle === "string"
        ? datos.detalle : "No se pudo obtener la configuración de reglas.";
      return { ok: false, mensaje: detalle, conflicto: respuesta.status === 409 };
    }
    return esConfiguracion(datos) ? { ok: true, configuracion: datos }
      : { ok: false, mensaje: "La configuración recibida no es válida." };
  } catch {
    return { ok: false, mensaje: "No se pudo conectar con el servicio de configuración." };
  }
}

export async function historialReglas(): Promise<ConfiguracionReglas[] | null> {
  try {
    const respuesta = await consultar("reglas-fiscalizacion/historial");
    if (!respuesta?.ok) return null;
    const datos: unknown = await respuesta.json();
    return Array.isArray(datos) && datos.every(esConfiguracion) ? datos : null;
  } catch { return null; }
}

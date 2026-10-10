import type { ClaimsGubUy } from "./oidc";

// Perfil del ciudadano en carga.uy, identificado por su cedula.
// Hoy el backend Frontofficeciudadanos (WildFly) no expone un endpoint para esto, asi que los
// perfiles se guardan en memoria del servidor de Next (se pierden al reiniciar). Cuando exista el
// endpoint REST, alcanza con reemplazar buscarOCrearPerfil por la llamada al backend.

export type PerfilCiudadano = {
  cedula: string;
  nombre: string;
  email?: string;
  emailVerificado?: boolean;
  subGubUy: string;
  nivelIdentidad?: number;
  creado: string;
  ultimoLogin: string;
};

const store: Map<string, PerfilCiudadano> = ((globalThis as { __perfilesCiudadanos?: Map<string, PerfilCiudadano> })
  .__perfilesCiudadanos ??= new Map());

// La cedula sale de numero_documento (scope document) o, si no vino, del uid (scope personal_info),
// que tiene la forma "uy-ci-12345678".
export function cedulaDe(claims: ClaimsGubUy): string | null {
  if (claims.numero_documento) return claims.numero_documento.replace(/\D/g, "");
  const m = claims.uid?.match(/^uy-ci-(\d+)$/i);
  return m ? m[1] : null;
}

export function nombreDe(claims: ClaimsGubUy): string {
  return (
    claims.nombre_completo ||
    [claims.primer_nombre, claims.segundo_nombre, claims.primer_apellido, claims.segundo_apellido]
      .filter(Boolean)
      .join(" ") ||
    "Ciudadano"
  );
}

export async function buscarOCrearPerfil(
  claims: ClaimsGubUy,
): Promise<{ perfil: PerfilCiudadano; nuevo: boolean }> {
  const cedula = cedulaDe(claims);
  if (!cedula) {
    throw new Error("gub.uy no devolvio la cedula (falta el scope document o personal_info)");
  }

  const ahora = new Date().toISOString();
  const existente = store.get(cedula);
  const perfil: PerfilCiudadano = {
    cedula,
    nombre: nombreDe(claims),
    email: claims.email ?? existente?.email,
    emailVerificado: claims.email_verified ?? existente?.emailVerificado,
    subGubUy: claims.sub,
    nivelIdentidad: claims.rid !== undefined ? Number(claims.rid) : existente?.nivelIdentidad,
    creado: existente?.creado ?? ahora,
    ultimoLogin: ahora,
  };
  store.set(cedula, perfil);
  return { perfil, nuevo: !existente };
}

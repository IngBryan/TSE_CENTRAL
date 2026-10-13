import { BackendNoDisponible, llamarBackend } from "../backend";
import { ErrorLogin, type ClaimsGubUy } from "./oidc";

// Perfil del ciudadano en carga.uy, identificado por su cedula. Lo busca o crea el backend
// Frontofficeciudadanos (WildFly), que ademas lo valida contra la PDI si hace falta (CU-01 pasos
// 6 a 8 y CU-03) y devuelve sus roles (paso 9).

export type EstadoPerfil = "PENDIENTE_VALIDACION" | "VALIDADO" | "RECHAZADO" | "NO_HABILITADO";
export type ResultadoValidacion = "VALIDADO" | "NO_EXISTE" | "MENOR_DE_EDAD" | "NO_DISPONIBLE";

// estado solo aplica a CHOFER: HABILITADO o PENDIENTE_VALIDACION
export type Rol = { rol: "RESPONSABLE" | "CHOFER"; nroEmpresa: number; empresa: string; estado?: string | null };

export type PerfilCiudadano = {
  cedula: string;
  nombre: string;
  email?: string;
  estado: EstadoPerfil;
  roles: Rol[];
};

type RespuestaIngreso = {
  resultado: ResultadoValidacion;
  sesionPermitida: boolean;
  nuevo: boolean;
  perfil: { cedula: string; email?: string; nombres?: string; apellidos?: string; estado: EstadoPerfil };
  roles?: Rol[];
};

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

export async function ingresarEnBackend(
  claims: ClaimsGubUy,
  correlationId: string,
): Promise<{ perfil: PerfilCiudadano; resultado: ResultadoValidacion; sesionPermitida: boolean; nuevo: boolean }> {
  const cedula = cedulaDe(claims);
  if (!cedula) {
    throw new ErrorLogin("token_invalido", "gub.uy no devolvio la cedula (falta el scope document o personal_info)");
  }

  let res;
  try {
    res = await llamarBackend<RespuestaIngreso>("/ciudadanos/ingreso-gubuy", {
      method: "POST",
      body: { cedula, email: claims.email, sub: claims.sub },
      correlationId,
    });
  } catch (e) {
    if (e instanceof BackendNoDisponible) throw new ErrorLogin("ingreso_no_disponible", e.message);
    throw e;
  }
  if (res.status !== 200 || !res.data) {
    throw new ErrorLogin("ingreso_no_disponible", `El backend respondio ${res.status}`);
  }

  const r = res.data;
  const nombrePdi = [r.perfil.nombres, r.perfil.apellidos].filter(Boolean).join(" ");
  return {
    perfil: {
      cedula: r.perfil.cedula,
      nombre: nombrePdi || nombreDe(claims),
      email: r.perfil.email,
      estado: r.perfil.estado,
      roles: r.roles ?? [],
    },
    resultado: r.resultado,
    sesionPermitida: r.sesionPermitida,
    nuevo: r.nuevo,
  };
}

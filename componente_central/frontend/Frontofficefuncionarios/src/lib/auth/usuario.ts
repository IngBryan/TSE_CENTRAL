import type { ClaimsGubUy } from "./oidc";

// La cedula sale de numero_documento (scope document) o, si no vino, del uid (scope personal_info),
// que tiene la forma "uy-ci-12345678". Mismo criterio que frontofficeciudadanos.
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
    "Funcionario"
  );
}

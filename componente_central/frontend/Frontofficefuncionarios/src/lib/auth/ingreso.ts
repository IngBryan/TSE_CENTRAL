import { BackendNoDisponible, llamarBackend } from "../backend";
import { ErrorLogin, type ClaimsGubUy, type MotivoError } from "./oidc";
import type { Usuario } from "./session";

// Ingreso del funcionario en carga.uy. El backend Frontofficefuncionarios (WildFly) controla que la
// cedula este registrada como funcionario del MTOP y la valida contra la PDI (DNIC: que exista y sea
// mayor de edad). Si la PDI no responde deja entrar igual (NO_DISPONIBLE) con el nombre de gub.uy.

type Resultado = "VALIDADO" | "NO_ES_FUNCIONARIO" | "NO_EXISTE" | "MENOR_DE_EDAD" | "NO_DISPONIBLE";

type RespuestaIngreso = {
  resultado: Resultado;
  sesionPermitida: boolean;
  cedula: string;
  email?: string | null;
  nombres?: string | null;
  apellidos?: string | null;
};

const MOTIVO_RECHAZO: Partial<Record<Resultado, MotivoError>> = {
  NO_ES_FUNCIONARIO: "no_es_funcionario",
  NO_EXISTE: "no_existe",
  MENOR_DE_EDAD: "menor_de_edad",
};

export type Ingreso = { usuario: Usuario; resultado: Resultado };

// La cedula sale de numero_documento (scope document) o, si no vino, del uid (scope personal_info),
// que tiene la forma "uy-ci-12345678".
export function cedulaDe(claims: ClaimsGubUy): string | null {
  if (claims.numero_documento) return claims.numero_documento.replace(/\D/g, "");
  const m = claims.uid?.match(/^uy-ci-(\d+)$/i);
  return m ? m[1] : null;
}

function nombreDe(claims: ClaimsGubUy): string {
  return (
    claims.nombre_completo ||
    [claims.primer_nombre, claims.segundo_nombre, claims.primer_apellido, claims.segundo_apellido]
      .filter(Boolean)
      .join(" ")
  );
}

export async function ingresarConGubUy(claims: ClaimsGubUy, correlationId: string): Promise<Ingreso> {
  const cedula = cedulaDe(claims);
  if (!cedula) {
    throw new ErrorLogin("token_invalido", "gub.uy no devolvio la cedula (falta el scope document o personal_info)");
  }
  return ingresar("/funcionarios/ingreso-gubuy", { cedula, email: claims.email }, nombreDe(claims) || `C.I. ${cedula}`, correlationId);
}

// Ingreso de testing (LOGIN_TESTING=true): los mismos controles del backend con la cedula ingresada a
// mano. El backend solo lo atiende con FUNCIONARIOS_INGRESO_TESTING=true (si no, 404).
export async function ingresarParaTesting(cedula: string, correlationId: string): Promise<Ingreso> {
  return ingresar("/funcionarios/ingreso-testing", { cedula }, `C.I. ${cedula}`, correlationId);
}

// Devuelve el usuario de la sesion, o ErrorLogin con el motivo si no se puede abrir.
async function ingresar(ruta: string, body: unknown, nombreAlternativo: string, correlationId: string): Promise<Ingreso> {
  let res;
  try {
    res = await llamarBackend<RespuestaIngreso>(ruta, { method: "POST", body, correlationId });
  } catch (e) {
    if (e instanceof BackendNoDisponible) throw new ErrorLogin("ingreso_no_disponible", e.message);
    throw e;
  }
  if (res.status !== 200 || !res.data) {
    throw new ErrorLogin("ingreso_no_disponible", `El backend respondio ${res.status}`);
  }

  const r = res.data;
  if (!r.sesionPermitida) {
    throw new ErrorLogin(MOTIVO_RECHAZO[r.resultado] ?? "no_es_funcionario", `resultado ${r.resultado}`);
  }
  const nombrePdi = [r.nombres, r.apellidos].filter(Boolean).join(" ");
  return {
    usuario: {
      cedula: r.cedula,
      nombre: nombrePdi || nombreAlternativo,
      email: r.email ?? undefined,
      identidadVerificada: r.resultado !== "NO_DISPONIBLE",
    },
    resultado: r.resultado,
  };
}

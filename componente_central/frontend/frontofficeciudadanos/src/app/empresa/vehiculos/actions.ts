"use server";

import { randomUUID } from "node:crypto";
import { revalidatePath } from "next/cache";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import { getSesion } from "@/lib/auth/session";

// ABM de Vehiculos de la empresa. La cedula del responsable sale de la sesion; el backend controla
// permisos y reglas (matricula normalizada y unica, baja logica, vigencia de permiso e ITV).

export type PermisoVista = { nro: string; fechaEmision: string; fechaVencimiento: string; vigente: boolean };
export type ItvVista = { id: number; fechaVencimiento: string; vigente: boolean };
// Permiso a demanda que un funcionario le exige a la empresa, cargado o no en este vehiculo
export type PermisoExtraVista = {
  idTipo: number;
  nombre: string;
  pideNumero: boolean;
  pideVencimiento: boolean;
  cargado: boolean;
  numero: string | null;
  fechaVencimiento: string | null;
  vigente: boolean;
};
export type Vehiculo = {
  matricula: string;
  marca: string;
  modelo: string;
  peso: number;
  capacidadCargaKg: number;
  activo: boolean;
  permiso?: PermisoVista | null;
  itv?: ItvVista | null;
  habilitado: boolean;
  permisosExtra: PermisoExtraVista[];
};
export type VehiculoDetalle = { vehiculo: Vehiculo; itvs: ItvVista[] };

export type ValoresVehiculo = { matricula: string; marca: string; modelo: string; peso: string; capacidadCargaKg: string };
export type EstadoNuevoVehiculo = { ok?: string; error?: string; campos?: Record<string, string>; valores?: ValoresVehiculo };
export type Resultado = { ok: true } | { ok: false; error: string; campos?: Record<string, string> };

type ErrorBackend = { detalle?: string; campos?: Record<string, string> };

const NO_DISPONIBLE = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";
const SESION_VENCIDA = "Tu sesión venció. Volvé a ingresar con gub.uy.";

async function llamar<T>(ruta: string, method: "GET" | "POST" | "PUT", body?: unknown) {
  const sesion = await getSesion();
  if (!sesion) return null;
  const correlationId = randomUUID();
  const res = await llamarBackend<T & ErrorBackend>(ruta, { method, body, correlationId, usuarioCi: sesion.perfil.cedula });
  console.info(`[vehiculos] correlationId=${correlationId} ${method} ${ruta} status=${res.status}`);
  return res;
}

// "" o texto no numerico -> null: el backend informa el campo
const kilos = (valor: string) => {
  const n = Number(valor);
  return valor.trim() !== "" && Number.isFinite(n) ? Math.trunc(n) : null;
};

export async function crearVehiculo(_previo: EstadoNuevoVehiculo, formulario: FormData): Promise<EstadoNuevoVehiculo> {
  const valores: ValoresVehiculo = {
    matricula: String(formulario.get("matricula") ?? ""),
    marca: String(formulario.get("marca") ?? ""),
    modelo: String(formulario.get("modelo") ?? ""),
    peso: String(formulario.get("peso") ?? ""),
    capacidadCargaKg: String(formulario.get("capacidadCargaKg") ?? ""),
  };
  try {
    const res = await llamar<Vehiculo>("/empresa/vehiculos", "POST", {
      ...valores,
      peso: kilos(valores.peso),
      capacidadCargaKg: kilos(valores.capacidadCargaKg),
    });
    if (!res) return { error: SESION_VENCIDA, valores };
    if (res.status === 400) return { error: "Revisá los datos marcados.", campos: res.data?.campos, valores };
    if (res.status === 403) return { error: `No podés registrar vehículos: ${res.data?.detalle ?? "no sos responsable"}.`, valores };
    if (res.status !== 200 || !res.data) return { error: "No se pudo guardar el vehículo.", valores };
    revalidatePath("/empresa/vehiculos");
    return { ok: res.data.matricula };
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return { error: NO_DISPONIBLE, valores };
  }
}

async function modificar(matricula: string, ruta: string, method: "POST" | "PUT", body?: unknown): Promise<Resultado> {
  try {
    const res = await llamar<unknown>(`/empresa/vehiculos/${encodeURIComponent(matricula)}${ruta}`, method, body);
    if (!res) return { ok: false, error: SESION_VENCIDA };
    if (res.status === 200) {
      revalidatePath("/empresa/vehiculos");
      revalidatePath(`/empresa/vehiculos/${matricula}`);
      return { ok: true };
    }
    if (res.status === 400) return { ok: false, error: res.data?.campos?.general ?? "Revisá los datos marcados.", campos: res.data?.campos };
    if (res.status === 409) return { ok: false, error: res.data?.detalle ?? "El vehículo no se puede modificar." };
    if (res.status === 404) return { ok: false, error: "El vehículo no existe." };
    if (res.status === 403) return { ok: false, error: `No podés modificar vehículos: ${res.data?.detalle ?? "no sos responsable"}.` };
    return { ok: false, error: "No se pudo guardar el cambio." };
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return { ok: false, error: NO_DISPONIBLE };
  }
}

export async function editarVehiculo(
  matricula: string,
  datos: { marca: string; modelo: string; peso: string; capacidadCargaKg: string },
): Promise<Resultado> {
  return modificar(matricula, "", "PUT", { ...datos, peso: kilos(datos.peso), capacidadCargaKg: kilos(datos.capacidadCargaKg) });
}

export async function registrarPermiso(
  matricula: string,
  datos: { nro: string; fechaEmision: string; fechaVencimiento: string },
): Promise<Resultado> {
  return modificar(matricula, "/permiso", "PUT", datos);
}

export async function registrarPermisoExtra(
  matricula: string,
  idTipo: number,
  datos: { numero: string; fechaVencimiento: string },
): Promise<Resultado> {
  return modificar(matricula, `/permisos-extra/${idTipo}`, "PUT", datos);
}

export async function agregarItv(matricula: string, fechaVencimiento: string): Promise<Resultado> {
  return modificar(matricula, "/itv", "POST", { fechaVencimiento });
}

export async function darDeBaja(matricula: string): Promise<Resultado> {
  return modificar(matricula, "/baja", "POST");
}

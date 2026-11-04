import { randomUUID } from "node:crypto";
import { NextResponse, type NextRequest } from "next/server";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import type { CasoDetalle } from "@/lib/casos";
import type { ViajeDetalle } from "@/lib/viajes";

/** BFF autenticado: el navegador nunca recibe la clave de WildFly. */
export async function GET(request: NextRequest, { params }: {
  params: Promise<{ id: string; recurso: string }>;
}) {
  const sesion = await getSesion();
  if (!sesion) return NextResponse.json({ error: "no_autorizado" }, { status: 401 });
  const { id, recurso } = await params;
  if (!/^\d+$/.test(id) || !["gps", "rutas", "ventana"].includes(recurso)) {
    return NextResponse.json({ error: "no_encontrado" }, { status: 404 });
  }
  const opciones = { correlationId: randomUUID(), usuarioCi: sesion.usuario.cedula };
  try {
    let ruta: string;
    if (recurso === "gps") {
      const indice = request.nextUrl.searchParams.get("indice") ?? "0";
      const limite = request.nextUrl.searchParams.get("limite") ?? "250";
      if (!/^\d{1,8}$/.test(indice) || !/^(?:[1-9]\d?|[1-4]\d\d|500)$/.test(limite)) {
        return NextResponse.json({ error: "parametros_invalidos" }, { status: 400 });
      }
      ruta = `/casos/${id}/gps?${new URLSearchParams({ indice, limite })}`;
    } else {
      // Comprobar el expediente y su Guía en WildFly; nunca aceptar un idGuia enviado por el navegador.
      const caso = await llamarBackend<CasoDetalle>(`/casos/${id}`, opciones);
      if (caso.status !== 200 || !caso.data) {
        return NextResponse.json({ error: "caso_no_disponible" }, {
          status: [403, 404].includes(caso.status) ? caso.status : 502,
        });
      }
      if (!caso.data.guia?.id) {
        return recurso === "rutas" ? NextResponse.json([]) : NextResponse.json({ error: "sin_guia" }, { status: 404 });
      }
      const idGuia = caso.data.guia.id;
      if (recurso === "ventana") {
        // Solo se devuelve el período oficial del viaje, no el expediente ni datos personales.
        // La trayectoria se recupera por el BFF de seguimiento, que valida la sesión.
        const viaje = await llamarBackend<ViajeDetalle>(`/viajes/${encodeURIComponent(idGuia)}`, opciones);
        if (viaje.status !== 200 || !viaje.data) {
          return NextResponse.json({ error: "viaje_no_disponible" }, {
            status: [403, 404].includes(viaje.status) ? viaje.status : 502,
          });
        }
        return NextResponse.json({
          idGuia,
          desde: viaje.data.inicio ?? viaje.data.fechaAsignacion,
          hasta: viaje.data.fin,
        }, { headers: { "Cache-Control": "private, no-store" } });
      }
      ruta = `/viajes/${encodeURIComponent(idGuia)}/rutas-autorizadas`;
    }
    const res = await llamarBackend<unknown>(ruta, opciones);
    if (res.status !== 200) {
      return NextResponse.json({ error: "consulta_geografica_fallida" }, {
        status: [400, 403, 404].includes(res.status) ? res.status : 502,
      });
    }
    return NextResponse.json(res.data, { headers: { "Cache-Control": "private, no-store" } });
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    return NextResponse.json({ error: "servicio_no_disponible" }, { status: 503 });
  }
}

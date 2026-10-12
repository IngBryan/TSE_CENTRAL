import type { NextRequest } from "next/server";
import { reenviarAlBackend } from "@/lib/movil/proxy";

// Viajes pendientes (ASIGNADA o EN_CURSO) del chofer del token.
export async function GET(req: NextRequest) {
  return reenviarAlBackend(req, "/movil/viajes", "GET");
}

import { redirect } from "next/navigation";

import { consultarIdentidad } from "@/lib/backoffice-sesion";
import { inicioPorRol } from "@/lib/rutas";

export default async function Home() {
  const identidad = await consultarIdentidad();

  redirect(identidad ? inicioPorRol(identidad.rol) : "/login");
}

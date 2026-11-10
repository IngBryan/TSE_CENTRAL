import { redirect } from "next/navigation";

import { consultarIdentidad } from "@/lib/backoffice-sesion";
import { destinoTrasLogin } from "@/lib/rutas";

export default async function Home() {
  const identidad = await consultarIdentidad();

  redirect(identidad ? destinoTrasLogin(identidad) : "/login");
}

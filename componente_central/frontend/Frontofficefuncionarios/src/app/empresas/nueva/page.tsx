import Link from "next/link";
import { redirect } from "next/navigation";
import { Encabezado } from "@/components/encabezado";
import { getSesion } from "@/lib/auth/session";
import { buscarUsuariosDisponibles } from "../actions";
import { CrearEmpresa } from "./crear-empresa";

// CU Gestionar empresas de transporte, FA1: alta de una empresa, con su responsable elegido entre los
// usuarios registrados y validados. Que quien la registra sea funcionario lo controla el backend.
export default async function NuevaEmpresa() {
  const sesion = await getSesion();
  if (!sesion) redirect("/");
  // La primera tanda de usuarios disponibles viene con la pagina; el buscador filtra por cedula
  const disponibles = await buscarUsuariosDisponibles("");

  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 px-4 py-12">
        <div className="flex flex-col gap-1">
          <Link href="/empresas" className="text-sm text-zinc-500 hover:underline">
            ← Empresas de transporte
          </Link>
          <h1 className="text-3xl font-semibold tracking-tight text-black dark:text-zinc-50">Nueva empresa</h1>
        </div>

        <CrearEmpresa disponibles={disponibles} />
      </main>
    </div>
  );
}

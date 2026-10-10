import { redirect } from "next/navigation";
import { Encabezado, botonSecundario } from "@/components/encabezado";
import { getSesion } from "@/lib/auth/session";

// Area privada: sin sesion se vuelve al portal publico.
export default async function MiCuenta({ searchParams }: PageProps<"/mi-cuenta">) {
  const params = await searchParams;
  const sesion = await getSesion();
  if (!sesion) redirect("/");

  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-6 px-4 py-12">
        <div className="flex flex-col gap-6 rounded-2xl bg-white p-10 shadow-sm dark:bg-zinc-950">
          <p className="text-lg text-zinc-700 dark:text-zinc-300">
            {params.bienvenida ? "Bienvenido/a, creamos tu perfil" : "Hola"}, {sesion.perfil.nombre}.
          </p>
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-zinc-700 dark:text-zinc-300">
            <dt className="font-medium">Cédula</dt>
            <dd>{sesion.perfil.cedula}</dd>
            <dt className="font-medium">Email</dt>
            <dd>{sesion.perfil.email ?? "—"}</dd>
          </dl>
          <a href="/auth/logout" className={botonSecundario}>
            Cerrar sesión
          </a>
        </div>
      </main>
    </div>
  );
}

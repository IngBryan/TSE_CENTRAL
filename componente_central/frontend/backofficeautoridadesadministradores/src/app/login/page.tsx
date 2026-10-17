
import { redirect } from "next/navigation";
import { consultarIdentidad } from "@/lib/backoffice-sesion";
import FormularioLogin from "./FormularioLogin";

export default async function LoginPage() {
  const identidad = await consultarIdentidad();

  if (identidad?.rol === "ADMINISTRADOR") {
    redirect("/nodos-perifericos");
  }

  return (
    <main className="flex flex-1 items-center justify-center px-4 py-16">
      <section className="w-full max-w-md rounded-xl border bg-card p-8 shadow-sm">
        <p className="mb-1 text-sm font-medium text-muted-foreground">
          CARGA.UY · Backoffice
        </p>

        <h1 className="mb-2 text-2xl font-semibold">
          Iniciar sesión
        </h1>

        <p className="mb-7 text-sm text-muted-foreground">
          Ingresá con tu usuario administrador para
          gestionar nodos periféricos.
        </p>

        <FormularioLogin />
      </section>
    </main>
  );
}

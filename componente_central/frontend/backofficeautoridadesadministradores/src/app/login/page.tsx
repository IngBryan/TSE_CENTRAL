import { Truck } from "lucide-react";
import { redirect } from "next/navigation";

import { consultarIdentidad } from "@/lib/backoffice-sesion";
import { inicioPorRol } from "@/lib/rutas";
import FormularioLogin from "./FormularioLogin";

export default async function LoginPage() {
  const identidad = await consultarIdentidad();

  if (identidad) {
    redirect(inicioPorRol(identidad.rol));
  }

  return (
    // Soft blue and violet glows behind the card: color without competing with the form
    <main className="relative flex flex-1 items-center justify-center overflow-hidden px-4 py-16">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-40 -left-40 size-[32rem] rounded-full bg-[#2a78d6]/15 blur-3xl"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -right-32 -bottom-40 size-[28rem] rounded-full bg-[#6250d6]/12 blur-3xl"
      />

      <section className="relative w-full max-w-md rounded-2xl border bg-card p-8 shadow-xl shadow-primary/5">
        <div className="mb-6 flex items-center gap-3">
          <span className="flex size-11 items-center justify-center rounded-xl bg-linear-to-br from-[#2a78d6] to-[#6250d6] text-white shadow-md">
            <Truck className="size-5" />
          </span>
          <div className="leading-tight">
            <p className="font-semibold tracking-tight">CARGA.UY</p>
            <p className="text-sm text-muted-foreground">Backoffice</p>
          </div>
        </div>

        <h1 className="mb-1.5 text-2xl font-semibold tracking-tight">Iniciar sesión</h1>

        <p className="mb-7 text-sm text-muted-foreground">
          Ingresá con tu usuario de administrador o de autoridad.
        </p>

        <FormularioLogin />
      </section>
    </main>
  );
}

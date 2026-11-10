import { KeyRound, LogOut } from "lucide-react";
import { redirect } from "next/navigation";

import FormularioCambioContrasena from "@/app/(backoffice)/cuenta/FormularioCambioContrasena";
import { cerrarSesionAction } from "@/app/login/actions";
import { Button } from "@/components/ui/button";
import { consultarIdentidad } from "@/lib/backoffice-sesion";
import { inicioPorRol, RUTA_CERRAR_SESION } from "@/lib/rutas";

/**
 * Primer ingreso con la contraseña que definió un administrador: hasta
 * cambiarla WildFly no deja usar nada más, así que la página va sin el menú
 * del backoffice (como el login) y solo permite cambiarla o salir.
 */
export default async function CambioObligatorioPage() {
  const identidad = await consultarIdentidad();

  if (!identidad) {
    redirect(RUTA_CERRAR_SESION);
  }

  // Ya la cambió (o nunca tuvo que hacerlo)
  if (!identidad.debeCambiarContrasena) {
    redirect(inicioPorRol(identidad.rol));
  }

  return (
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
            <KeyRound className="size-5" />
          </span>
          <div className="leading-tight">
            <p className="font-semibold tracking-tight">CARGA.UY</p>
            <p className="text-sm text-muted-foreground">Backoffice · {identidad.ci}</p>
          </div>
        </div>

        <h1 className="mb-1.5 text-2xl font-semibold tracking-tight">Elegí tu contraseña</h1>

        <p className="mb-7 text-sm text-muted-foreground">
          Es tu primer ingreso y la contraseña que tenés te la dio un
          administrador. Para continuar, cambiala por una que solo sepas vos.
        </p>

        <FormularioCambioContrasena obligatorio />

        <form action={cerrarSesionAction} className="mt-6 border-t pt-4">
          <Button type="submit" variant="ghost" className="w-full gap-1.5 text-muted-foreground">
            <LogOut />
            Cerrar sesión
          </Button>
        </form>
      </section>
    </main>
  );
}

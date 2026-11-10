import { requerirRol } from "@/lib/backoffice-sesion";
import FormularioCambioContrasena from "./FormularioCambioContrasena";

export default async function CuentaPage() {
  const identidad = await requerirRol("ADMINISTRADOR", "AUTORIDAD");

  return (
    <div className="w-full min-w-0 space-y-6 px-6 py-8 lg:px-8">
      <header>
        <h1 className="text-3xl font-semibold tracking-tight">Mi cuenta</h1>

        <p className="mt-2 text-muted-foreground">
          Cédula {identidad.ci}
        </p>
      </header>

      <FormularioCambioContrasena />
    </div>
  );
}

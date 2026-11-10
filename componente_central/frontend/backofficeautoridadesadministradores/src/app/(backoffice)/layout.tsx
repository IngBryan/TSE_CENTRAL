import { cookies } from "next/headers";
import { redirect } from "next/navigation";

import MenuLateral, { BarraSuperior } from "@/components/MenuLateral";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";
import { consultarIdentidad } from "@/lib/backoffice-sesion";
import { RUTA_CAMBIO_OBLIGATORIO, RUTA_CERRAR_SESION } from "@/lib/rutas";

// Every page with a session shares the navigation menu; each page still checks its own role
export default async function BackofficeLayout({ children }: LayoutProps<"/">) {
  const identidad = await consultarIdentidad();

  if (!identidad) {
    redirect(RUTA_CERRAR_SESION);
  }

  // Hasta cambiar la contraseña inicial no anda ninguna sección (WildFly responde 403): ni siquiera se muestra el menú
  if (identidad.debeCambiarContrasena) {
    redirect(RUTA_CAMBIO_OBLIGATORIO);
  }

  // The sidebar saves whether it was open in this cookie; reading it here avoids a jump on the first paint
  const abierto = (await cookies()).get("sidebar_state")?.value !== "false";

  return (
    <SidebarProvider defaultOpen={abierto}>
      <MenuLateral ci={identidad.ci} rol={identidad.rol} />
      <SidebarInset className="min-w-0">
        <BarraSuperior />
        <div className="flex min-w-0 flex-1">{children}</div>
      </SidebarInset>
    </SidebarProvider>
  );
}

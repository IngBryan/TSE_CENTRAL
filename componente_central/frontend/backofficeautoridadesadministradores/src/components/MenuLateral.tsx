"use client";

import {
  FileText,
  LayoutDashboard,
  LogOut,
  Network,
  Settings,
  Truck,
  Users,
  type LucideIcon,
} from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { cerrarSesionAction } from "@/app/login/actions";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Separator } from "@/components/ui/separator";
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
  SidebarTrigger,
} from "@/components/ui/sidebar";
import { inicioPorRol, rolesPermitidos, type Rol } from "@/lib/rutas";

// Each role only sees the entries that RUTAS_PROTEGIDAS lets it open
const ENTRADAS: { ruta: string; nombre: string; icono: LucideIcon }[] = [
  { ruta: "/dashboard", nombre: "Dashboards", icono: LayoutDashboard },
  { ruta: "/reportes", nombre: "Reportes", icono: FileText },
  { ruta: "/nodos-perifericos", nombre: "Nodos periféricos", icono: Network },
  { ruta: "/reglas-fiscalizacion", nombre: "Reglas de fiscalización", icono: Settings },
  { ruta: "/usuarios", nombre: "Usuarios", icono: Users },
];

const NOMBRE_ROL: Record<Rol, string> = {
  ADMINISTRADOR: "Administrador",
  AUTORIDAD: "Autoridad",
};

const esActual = (ruta: string, destino: string) => ruta === destino || ruta.startsWith(`${destino}/`);

function Marca({ className = "" }: { className?: string }) {
  return (
    <span
      className={`flex size-8 shrink-0 items-center justify-center rounded-lg bg-linear-to-br from-[#2a78d6] to-[#6250d6] text-white shadow-sm ${className}`}
    >
      <Truck className="size-4" />
    </span>
  );
}

export default function MenuLateral({ ci, rol }: { ci: string; rol: Rol }) {
  const ruta = usePathname();
  const entradas = ENTRADAS.filter((e) => rolesPermitidos(e.ruta)?.includes(rol));

  return (
    // Collapses to its icons (each with a tooltip); on phones it opens as a sheet
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" tooltip="CARGA.UY" render={<Link href={inicioPorRol(rol)} />}>
              <Marca />
              <span className="grid leading-tight">
                <span className="font-semibold tracking-tight">CARGA.UY</span>
                <span className="text-xs text-muted-foreground">Backoffice</span>
              </span>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel>Menú</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {entradas.map(({ ruta: destino, nombre, icono: Icono }) => (
                <SidebarMenuItem key={destino}>
                  <SidebarMenuButton
                    isActive={esActual(ruta, destino)}
                    tooltip={nombre}
                    render={<Link href={destino} />}
                  >
                    <Icono />
                    <span>{nombre}</span>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      <SidebarFooter>
        <SidebarMenu>
          <SidebarMenuItem>
            <div
              className="flex items-center gap-2 overflow-hidden rounded-md p-1 group-data-[collapsible=icon]:p-0"
              title={`${ci} · ${NOMBRE_ROL[rol]}`}
            >
              <Avatar className="size-8">
                <AvatarFallback className="bg-sidebar-accent text-xs font-semibold text-sidebar-accent-foreground">
                  {NOMBRE_ROL[rol].charAt(0)}
                </AvatarFallback>
              </Avatar>
              <span className="grid text-xs leading-tight group-data-[collapsible=icon]:hidden">
                <span className="font-medium">{ci}</span>
                <span className="text-muted-foreground">{NOMBRE_ROL[rol]}</span>
              </span>
            </div>
          </SidebarMenuItem>
          <SidebarMenuItem>
            <form action={cerrarSesionAction}>
              <SidebarMenuButton type="submit" tooltip="Cerrar sesión">
                <LogOut />
                <span>Cerrar sesión</span>
              </SidebarMenuButton>
            </form>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>

      <SidebarRail />
    </Sidebar>
  );
}

// Top bar of the content: the button that folds the menu (Ctrl+B) and where you are
export function BarraSuperior() {
  const ruta = usePathname();
  const seccion = ENTRADAS.find((e) => esActual(ruta, e.ruta))?.nombre;

  return (
    <header className="sticky top-0 z-20 flex h-12 shrink-0 items-center gap-2 border-b bg-background/80 px-4 backdrop-blur print:hidden">
      <SidebarTrigger className="-ml-1" />
      <Separator orientation="vertical" className="mx-1 data-vertical:h-4" />
      <Marca className="size-6 md:hidden [&_svg]:size-3" />
      <span className="text-sm font-medium">{seccion ?? "Backoffice"}</span>
    </header>
  );
}

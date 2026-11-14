"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";

// Entrada de la barra superior, resaltada cuando se esta en esa seccion (o en una subpagina)
export function NavLink({ href, children }: { href: string; children: ReactNode }) {
  const ruta = usePathname();
  const activa = ruta === href || ruta.startsWith(`${href}/`);
  return (
    <Link
      href={href}
      aria-current={activa ? "page" : undefined}
      className={`flex h-9 items-center gap-2 rounded-lg px-3 text-sm font-medium transition-colors [&_svg]:size-4 ${
        activa ? "bg-accent text-accent-foreground" : "text-muted-foreground hover:bg-accent hover:text-accent-foreground"
      }`}
    >
      {children}
    </Link>
  );
}

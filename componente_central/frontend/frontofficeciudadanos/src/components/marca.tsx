import { Truck } from "lucide-react";

// Logo de carga.uy: el mismo camion sobre degradado azul-violeta que usa el backoffice
export function Marca({ className = "" }: { className?: string }) {
  return (
    <span
      className={`flex size-8 shrink-0 items-center justify-center rounded-lg bg-linear-to-br from-[#2a78d6] to-[#6250d6] text-white shadow-sm ${className}`}
    >
      <Truck className="size-4" />
    </span>
  );
}

// Brillos azul y violeta detras del contenido (portadas), como en el login del backoffice
export function Brillos() {
  return (
    <>
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-40 -left-40 size-[32rem] rounded-full bg-[#2a78d6]/15 blur-3xl"
      />
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -right-32 top-40 size-[28rem] rounded-full bg-[#6250d6]/12 blur-3xl"
      />
    </>
  );
}

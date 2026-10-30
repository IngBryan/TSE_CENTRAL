import { COLOR_HABILITADA } from "@/lib/empresas";

// Si la empresa esta habilitada por el MTOP (solo una habilitada puede dar de alta choferes)
export function Habilitada({ habilitada }: { habilitada: boolean }) {
  return (
    <span
      className={`whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium ${
        habilitada ? COLOR_HABILITADA.si : COLOR_HABILITADA.no
      }`}
    >
      {habilitada ? "Habilitada" : "No habilitada"}
    </span>
  );
}

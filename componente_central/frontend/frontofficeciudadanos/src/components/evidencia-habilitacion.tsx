import { fecha, fechaHora } from "@/lib/infracciones";
import type { PruebaHabilitacion, EstadoDocumentoHabilitacion } from "@/lib/habilitacion";

const ESTADOS: Record<EstadoDocumentoHabilitacion, string> = {
  VIGENTE: "Vigente",
  VENCIDO: "Vencido",
  AUN_NO_VIGENTE: "Aún no vigente",
  SIN_REGISTRO: "Sin registro",
};

export function EvidenciaHabilitacion({ pruebas }: { pruebas: PruebaHabilitacion[] }) {
  return (
    <div className="rounded-2xl bg-white p-6 text-sm shadow-sm dark:bg-zinc-950">
      <h3 className="font-medium text-black dark:text-zinc-50">Permiso de circulación e ITV</h3>
      <p className="mt-1 text-zinc-600 dark:text-zinc-400">
        Documentos considerados a la fecha del evento. La falta de registro o de vigencia de cualquiera de los dos origina la infracción.
      </p>
      {pruebas.length === 0 ? (
        <p className="mt-3 text-zinc-600 dark:text-zinc-400">Este caso no tiene una evaluación documental registrada.</p>
      ) : <div className="mt-4 flex flex-col gap-4">
        {pruebas.map((p) => (
          <div key={`${p.origen}-${p.fechaHora}`} className="border-t border-black/10 pt-4 dark:border-white/10">
            <p className="mb-3 font-medium">{fechaHora(p.fechaHora)} · {p.origen === "PESAJE" ? "Pesaje" : "GPS"}</p>
            <div className="grid gap-3 sm:grid-cols-2">
              <Documento titulo="Permiso nacional de circulación" estado={p.estadoPermiso}
                numero={p.nroPermiso} emision={p.emisionPermiso} vencimiento={p.vencimientoPermiso} />
              <Documento titulo="Inspección técnica vehicular (ITV)" estado={p.estadoItv}
                emision={p.emisionItv} vencimiento={p.vencimientoItv} />
            </div>
          </div>
        ))}
      </div>}
    </div>
  );
}

function Documento({ titulo, estado, numero, emision, vencimiento }: {
  titulo: string; estado: EstadoDocumentoHabilitacion; numero?: string | null;
  emision: string | null; vencimiento: string | null;
}) {
  const vigente = estado === "VIGENTE";
  return <div className={`rounded-xl border p-3 ${vigente
    ? "border-green-200 bg-green-50 dark:border-green-900 dark:bg-green-950"
    : "border-red-200 bg-red-50 dark:border-red-900 dark:bg-red-950"}`}>
    <p className="font-medium">{titulo}</p>
    <p className={vigente ? "text-green-800 dark:text-green-200" : "font-semibold text-red-800 dark:text-red-200"}>
      {ESTADOS[estado]}
    </p>
    {numero && <p>N.º {numero}</p>}
    {emision && <p>Emisión: {fecha(emision)}</p>}
    {vencimiento && <p>Vencimiento: {fecha(vencimiento)}</p>}
  </div>;
}

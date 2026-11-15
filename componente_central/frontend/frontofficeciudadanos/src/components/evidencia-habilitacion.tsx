import { fecha, fechaHora } from "@/lib/infracciones";
import type { PruebaHabilitacion, EstadoDocumentoHabilitacion } from "@/lib/habilitacion";

const ESTADOS: Record<EstadoDocumentoHabilitacion, string> = {
  VIGENTE: "Vigente",
  VENCIDO: "Vencido",
  AUN_NO_VIGENTE: "Aún no vigente",
  SIN_REGISTRO: "Sin registro",
};

export function EvidenciaHabilitacion({ pruebas }: { pruebas: PruebaHabilitacion[] }) {
  // Mostrar una vez los documentos que acreditaron la infracción inicial.
  const prueba = pruebas.reduce<PruebaHabilitacion | null>((primera, actual) =>
    !primera || Date.parse(actual.fechaHora) < Date.parse(primera.fechaHora) ? actual : primera, null);
  return (
    <div className="rounded-xl border bg-card p-6 text-sm shadow-sm">
      <h3 className="font-medium text-foreground">Permiso de circulación e ITV</h3>
      {prueba ? <>
        <p className="mt-2 text-muted-foreground">Evaluación inicial: {fechaHora(prueba.fechaHora)} · {prueba.origen === "PESAJE" ? "Pesaje" : "GPS"}</p>
        <div className="mt-4 grid gap-3 sm:grid-cols-2">
          <Documento titulo="Permiso nacional de circulación" estado={prueba.estadoPermiso}
            numero={prueba.nroPermiso} emision={prueba.emisionPermiso} vencimiento={prueba.vencimientoPermiso} />
          <Documento titulo="Inspección técnica vehicular (ITV)" estado={prueba.estadoItv}
            emision={prueba.emisionItv} vencimiento={prueba.vencimientoItv} />
        </div>
      </> : <p className="mt-3 text-muted-foreground">Este caso no tiene una evaluación documental registrada.</p>}
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

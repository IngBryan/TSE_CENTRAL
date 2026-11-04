// Logs del front: una linea JSON con el mismo formato que LogEstructurado del backend, para seguir una operacion
// por su correlationId entre los componentes. Lo propio de cada pantalla va en datos.
const COMPONENTE = "backoffice-front";

type Nivel = "info" | "warn" | "error";

type Campos = {
  correlationId?: string;
  usuario?: string;
  responseCode?: number;
  roundtripSeconds?: number;
  backendRoundtripSeconds?: number;
  error?: string;
};

const segundos = (valor?: number) => (valor === undefined ? undefined : Number(valor.toFixed(3)));

export function segundosDesde(inicioMs: number): number {
  return (performance.now() - inicioMs) / 1000;
}

export function registrarLog(nivel: Nivel, evento: string, campos: Campos = {}, datos?: Record<string, unknown>) {
  console[nivel](
    JSON.stringify({
      timestamp: new Date().toISOString(),
      nivel: nivel.toUpperCase(),
      componente: COMPONENTE,
      evento,
      ...campos,
      roundtripSeconds: segundos(campos.roundtripSeconds),
      backendRoundtripSeconds: segundos(campos.backendRoundtripSeconds),
      datos,
    }),
  );
}

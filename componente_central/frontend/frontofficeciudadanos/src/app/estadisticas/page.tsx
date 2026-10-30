import { randomUUID } from "node:crypto";
import Link from "next/link";
import { Encabezado } from "@/components/encabezado";
import { getSesion } from "@/lib/auth/session";
import { BackendNoDisponible, llamarBackend } from "@/lib/backend";
import { fechaHora, tipoInfraccion } from "@/lib/infracciones";
import {
  decimal,
  numero,
  toneladas,
  type Estadisticas,
  type FilaPublica,
  type Indicador as ValorIndicador,
  type Tabla as TablaCsv,
  type Valor,
} from "@/lib/portal";

const tarjeta = "rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950";
const titulo = "text-lg font-semibold text-black dark:text-zinc-50";
const RESERVADO = "Reservado";

// Listados de Informacion Publica (portal publico, sin login): estadisticas agregadas del transporte de carga.
// El backend ya aplico el umbral de agregacion: lo reservado llega sin valor. Por ahora solo se muestra el
// anio en curso (todavia no hay datos de anios anteriores); el backend ya acepta ?anio= para cuando haga falta.
export default async function EstadisticasPublicas() {
  const sesion = await getSesion();

  let datos: Estadisticas | null = null;
  let error: string | null = null;
  try {
    const res = await llamarBackend<Estadisticas>("/publico/estadisticas", { correlationId: randomUUID() });
    if (res.status === 200) datos = res.data;
    else error = "No se pudieron obtener las estadísticas.";
  } catch (e) {
    if (!(e instanceof BackendNoDisponible)) throw e;
    error = "El servicio no está disponible en este momento. Probá de nuevo en unos minutos.";
  }

  return (
    <div className="flex flex-1 flex-col bg-zinc-50 font-sans dark:bg-black">
      <Encabezado sesion={sesion} />

      <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-8 px-4 py-12">
        <div className="flex flex-col gap-2">
          <Link href="/" className="text-sm text-zinc-500 hover:underline">
            ← Inicio
          </Link>
          <h1 className="text-3xl font-semibold tracking-tight text-black dark:text-zinc-50">
            El transporte de carga en números
          </h1>
          <p className="text-zinc-600 dark:text-zinc-400">
            Información pública y agregada sobre empresas, vehículos, viajes e infracciones del transporte de carga por
            carretera.
          </p>
        </div>

        {error || !datos ? (
          <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
            {error}
          </p>
        ) : (
          <Contenido d={datos} />
        )}
      </main>
    </div>
  );
}

function Contenido({ d }: { d: Estadisticas }) {
  return (
    <>
      <section className="grid gap-4 sm:grid-cols-4">
        <Indicador etiqueta="Empresas habilitadas" valor={d.empresasHabilitadas} aclaracion="hoy" />
        <Indicador etiqueta="Vehículos habilitados" valor={d.vehiculosHabilitados} aclaracion="hoy" />
        <Indicador etiqueta="Viajes realizados" valor={d.viajes} aclaracion={String(d.anio)} />
        <Indicador etiqueta="Carga transportada" valor={d.kgTransportados} aclaracion={String(d.anio)} formato={toneladas} />
      </section>

      <section className="flex flex-col gap-3">
        <h2 className={titulo}>Fiscalización ({d.anio})</h2>
        <div className="grid gap-4 sm:grid-cols-4">
          <Indicador
            etiqueta="Infracciones cada 100 viajes"
            valor={d.infraccionesCada100Viajes}
            aclaracion="confirmadas"
            formato={decimal}
          />
          <Indicador etiqueta="Casos resueltos" valor={d.casosResueltos} aclaracion="confirmados o desestimados" />
          <Indicador
            etiqueta="Casos desestimados"
            valor={d.porcentajeDesestimados}
            aclaracion="del total de resueltos"
            formato={(n) => (n == null ? "-" : `${decimal(n)} %`)}
          />
          <Indicador
            etiqueta="Tiempo de resolución"
            valor={d.medianaDiasResolucion}
            aclaracion="mediana, de la detección a la resolución"
            formato={(n) => (n == null ? "-" : `${decimal(n)} ${n === 1 ? "día" : "días"}`)}
          />
        </div>
      </section>

      <Tabla
        titulo={`Viajes realizados por mes (${d.anio})`}
        tabla="meses"
        filas={d.viajesPorMes}
        primera="Mes"
        etiqueta={(f) => f.etiqueta.charAt(0).toUpperCase() + f.etiqueta.slice(1)}
        columnas={[
          { titulo: "Viajes", valor: (f) => numero(f.cantidad) },
          { titulo: "Carga", valor: (f) => toneladas(f.kg) },
          { titulo: "Promedio por viaje", valor: (f) => (f.cantidad ? toneladas((f.kg ?? 0) / f.cantidad) : "-") },
        ]}
      />
      <Tabla
        titulo={`Carga transportada por rubro del cliente (${d.anio})`}
        tabla="rubros"
        filas={d.cargaPorRubro}
        primera="Rubro"
        etiqueta={(f) => f.etiqueta}
        barra={(f) => f.kg}
        columnas={[
          { titulo: "Viajes", valor: (f) => numero(f.cantidad) },
          { titulo: "Carga", valor: (f) => toneladas(f.kg) },
          { titulo: "Promedio por viaje", valor: (f) => (f.cantidad ? toneladas((f.kg ?? 0) / f.cantidad) : "-") },
        ]}
        vacio="No hay viajes finalizados en el año."
      />
      <Tabla
        titulo={`Infracciones confirmadas por tipo (${d.anio})`}
        tabla="infracciones"
        filas={d.infraccionesConfirmadasPorTipo}
        primera="Tipo"
        etiqueta={(f) => tipoInfraccion(f.etiqueta)}
        columnas={[
          { titulo: "Infracciones", valor: (f) => numero(f.cantidad) },
          // Cada 100 viajes del total del anio: solo si el total de viajes se publica
          {
            titulo: "Cada 100 viajes",
            valor: (f) =>
              d.viajes.valor && f.cantidad != null ? decimal(Math.round((1000 * f.cantidad) / d.viajes.valor) / 10) : "-",
          },
        ]}
        vacio="No hay infracciones confirmadas en el año."
      />

      <section className={`flex flex-col gap-2 text-sm text-zinc-600 dark:text-zinc-400 ${tarjeta}`}>
        <h2 className={titulo}>Cómo se protegen los datos</h2>
        <p>
          Para cumplir con la Ley 18.331 de protección de datos personales, cada número se publica solo si reúne datos
          de al menos <strong>{d.umbralEmpresas} empresas distintas</strong>. Con menos, una empresa podría deducir los
          datos de otra restando los propios. Los valores que no alcanzan ese mínimo figuran como «{RESERVADO}», y
          cuando hace falta se reserva también otro valor para que no se puedan calcular restando del total. Los
          rubros reservados no se muestran por nombre, porque el nombre podría identificar a la empresa: van
          sumados en «Otros rubros».
        </p>
        <p>
          Se cuentan los viajes finalizados y solo las infracciones confirmadas por el MTOP. Los indicadores de
          fiscalización toman los casos detectados en el año; el tiempo de resolución es la mediana (la mitad de los
          casos se resolvió en menos tiempo), para que un caso que demoró mucho no distorsione el valor.
        </p>
        <p>Cada tabla se puede descargar en CSV (separado por punto y coma) para reutilizar los datos.</p>
        <p className="text-xs">Datos generados: {fechaHora(d.generadas)}</p>
      </section>
    </>
  );
}

function Indicador({
  etiqueta,
  valor,
  aclaracion,
  formato = numero,
}: {
  etiqueta: string;
  valor: Valor | ValorIndicador;
  aclaracion: string;
  formato?: (n: number | null) => string;
}) {
  return (
    <div className={`flex flex-col gap-1 ${tarjeta}`}>
      <p className="text-sm text-zinc-500">{etiqueta}</p>
      <p className="text-2xl font-semibold tabular-nums text-black dark:text-zinc-50">
        {valor.reservado ? <span className="text-base font-normal text-zinc-500">{RESERVADO}</span> : formato(valor.valor)}
      </p>
      <p className="text-xs text-zinc-500">{aclaracion}</p>
    </div>
  );
}

type Columna = { titulo: string; valor: (f: FilaPublica) => string };

// Tabla con una barra proporcional bajo cada etiqueta (por defecto, a la cantidad) y enlace al CSV
function Tabla({
  titulo: texto,
  tabla,
  filas,
  primera,
  etiqueta,
  columnas,
  barra = (f) => f.cantidad,
  vacio,
}: {
  titulo: string;
  tabla: TablaCsv;
  filas: FilaPublica[];
  primera: string;
  etiqueta: (f: FilaPublica) => string;
  columnas: Columna[];
  barra?: (f: FilaPublica) => number | null;
  vacio?: string;
}) {
  const maximo = Math.max(0, ...filas.map((f) => (f.reservado ? 0 : (barra(f) ?? 0))));
  return (
    <section className="flex flex-col gap-3">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className={titulo}>{texto}</h2>
        {filas.length > 0 && (
          <a href={`/estadisticas/csv?tabla=${tabla}`} className="text-sm text-[#0068b3] hover:underline">
            Descargar CSV
          </a>
        )}
      </div>
      {filas.length === 0 ? (
        <p className="text-zinc-600 dark:text-zinc-400">{vacio}</p>
      ) : (
        <div className="overflow-x-auto rounded-2xl bg-white shadow-sm dark:bg-zinc-950">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-black/5 text-zinc-500 dark:border-white/10">
              <tr>
                <th className="px-4 py-3 font-medium">{primera}</th>
                {columnas.map((c) => (
                  <th key={c.titulo} className="px-4 py-3 text-right font-medium">
                    {c.titulo}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {filas.map((f) => {
                const ancho = !f.reservado && maximo > 0 ? ((barra(f) ?? 0) / maximo) * 100 : 0;
                return (
                  <tr key={f.clave} className="border-b border-black/5 last:border-0 dark:border-white/10">
                    <td className="min-w-40 px-4 py-3">
                      {etiqueta(f)}
                      <div className="mt-1 h-1.5 rounded-full bg-black/5 dark:bg-white/10" aria-hidden>
                        {ancho > 0 && <div className="h-full rounded-full bg-[#0068b3]" style={{ width: `${ancho}%` }} />}
                      </div>
                    </td>
                    {f.reservado ? (
                      <td colSpan={columnas.length} className="px-4 py-3 text-right text-zinc-500">
                        {RESERVADO}
                      </td>
                    ) : (
                      columnas.map((c) => (
                        <td key={c.titulo} className="px-4 py-3 text-right tabular-nums">
                          {c.valor(f)}
                        </td>
                      ))
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

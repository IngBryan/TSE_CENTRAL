import Link from "next/link";
import { EstadoPeriodo, EstadoVehiculoIndicador } from "@/components/estado-vigencia";
import {
  FILTROS,
  cumpleFiltro,
  fecha,
  type FiscalizacionEmpresa,
  type Filtro,
  type PeriodoVista,
} from "@/lib/vehiculos";

const tarjeta = "rounded-2xl bg-white p-6 text-sm shadow-sm dark:bg-zinc-950";

// CU Fiscalizacion de los permisos y estado de los vehiculos: los vehiculos de la empresa con el vencimiento
// de su permiso nacional de circulacion y de su ITV (el periodo completo y el historial estan en la pagina
// de cada vehiculo). El filtro va en la URL (?estado=) para que la vista se pueda compartir.
export function VehiculosEmpresa({
  nro,
  fiscalizacion,
  error,
  filtro,
}: {
  nro: string;
  fiscalizacion?: FiscalizacionEmpresa;
  error?: string;
  filtro: Filtro;
}) {
  return (
    <section id="vehiculos" className={`flex scroll-mt-6 flex-col gap-4 ${tarjeta}`}>
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-lg font-semibold text-black dark:text-zinc-50">Vehículos y habilitaciones</h2>
        {fiscalizacion && <p className="text-xs text-zinc-500">Vigencia al {fecha(fiscalizacion.fecha)}</p>}
      </div>

      {error || !fiscalizacion ? (
        <p role="alert" className="rounded-lg bg-zinc-100 p-4 text-zinc-700 dark:bg-zinc-900 dark:text-zinc-300">
          {error ?? "No se pudieron obtener los vehículos de la empresa."}
        </p>
      ) : fiscalizacion.resumen.total === 0 ? (
        <p className="text-zinc-600 dark:text-zinc-400">La empresa no tiene vehículos registrados.</p>
      ) : (
        <Listado nro={nro} fiscalizacion={fiscalizacion} filtro={filtro} />
      )}
    </section>
  );
}

function Listado({ nro, fiscalizacion, filtro }: { nro: string; fiscalizacion: FiscalizacionEmpresa; filtro: Filtro }) {
  const { resumen } = fiscalizacion;
  const cantidades: Record<Filtro, number> = {
    todos: resumen.total,
    no_habilitados: resumen.noHabilitados,
    por_vencer: resumen.porVencer,
    de_baja: resumen.dadosDeBaja,
  };
  const vehiculos = fiscalizacion.vehiculos.filter((v) => cumpleFiltro(v, filtro));

  return (
    <>
      {/* Los filtros hacen tambien de resumen: cada uno lleva su cantidad. Los vacios no se muestran. */}
      <nav aria-label="Filtrar vehículos" className="flex flex-wrap gap-1 border-b border-black/5 dark:border-white/10">
        {(Object.keys(FILTROS) as Filtro[])
          .filter((f) => f === "todos" || f === filtro || cantidades[f] > 0)
          .map((f) => (
            <Link
              key={f}
              href={f === "todos" ? `/empresas/${nro}#vehiculos` : `/empresas/${nro}?estado=${f}#vehiculos`}
              aria-current={f === filtro ? "page" : undefined}
              scroll={false}
              className={`-mb-px border-b-2 px-3 py-2 ${
                f === filtro
                  ? "border-[#0068b3] font-medium text-black dark:border-sky-400 dark:text-zinc-50"
                  : "border-transparent text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200"
              }`}
            >
              {FILTROS[f]} <span className="text-zinc-400">{cantidades[f]}</span>
            </Link>
          ))}
      </nav>

      {vehiculos.length === 0 ? (
        <p className="text-zinc-600 dark:text-zinc-400">No hay vehículos con ese estado.</p>
      ) : (
        <>
          {/* Celular: una fila compacta por vehiculo en vez de la tabla */}
          <ul className="-mx-6 divide-y divide-black/5 border-t border-black/5 sm:hidden dark:divide-white/10 dark:border-white/10">
            {vehiculos.map((v) => (
              <li key={v.matricula}>
                <Link
                  href={`/empresas/${nro}/vehiculos/${encodeURIComponent(v.matricula)}`}
                  className="flex flex-col gap-1 px-6 py-3 hover:bg-zinc-50 dark:hover:bg-zinc-900"
                >
                  <span className="flex items-center justify-between gap-3">
                    <span className="font-mono font-medium text-[#0068b3] dark:text-sky-400">{v.matricula}</span>
                    <EstadoVehiculoIndicador estado={v.estado} />
                  </span>
                  <span className="flex flex-wrap gap-x-4 gap-y-1 text-xs">
                    <span className="flex items-center gap-1">
                      <span className="text-zinc-500">Permiso</span> <EstadoPeriodo periodo={v.permiso} />
                    </span>
                    <span className="flex items-center gap-1">
                      <span className="text-zinc-500">ITV</span> <EstadoPeriodo periodo={v.itv} />
                    </span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
          <div className="-mx-6 hidden overflow-x-auto sm:block">
            <table className="w-full text-left text-sm">
              <thead className="text-xs text-zinc-500">
                <tr>
                  <th className="px-6 py-2 font-normal">Vehículo</th>
                  <th className="px-3 py-2 font-normal">Permiso de circulación</th>
                  <th className="px-3 py-2 font-normal">ITV</th>
                  <th className="px-6 py-2 font-normal">Estado</th>
                </tr>
              </thead>
              <tbody>
                {vehiculos.map((v) => (
                  <tr key={v.matricula} className="border-t border-black/5 align-top dark:border-white/10">
                    <td className="px-6 py-3">
                      <Link
                        href={`/empresas/${nro}/vehiculos/${encodeURIComponent(v.matricula)}`}
                        className="font-mono font-medium text-[#0068b3] hover:underline dark:text-sky-400"
                      >
                        {v.matricula}
                      </Link>
                      <p className="text-xs text-zinc-500">{[v.marca, v.modelo].filter(Boolean).join(" ") || "-"}</p>
                    </td>
                    <td className="px-3 py-3">
                      <Vencimiento periodo={v.permiso} />
                    </td>
                    <td className="px-3 py-3">
                      <Vencimiento periodo={v.itv} />
                    </td>
                    <td className="px-6 py-3">
                      <EstadoVehiculoIndicador estado={v.estado} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </>
  );
}

// En la tabla: su estado y, debajo, hasta cuando vale
function Vencimiento({ periodo }: { periodo: PeriodoVista }) {
  return (
    <div className="flex flex-col gap-0.5">
      <EstadoPeriodo periodo={periodo} />
      {periodo.hasta && <span className="pl-3.5 text-xs text-zinc-500">Hasta el {fecha(periodo.hasta)}</span>}
    </div>
  );
}

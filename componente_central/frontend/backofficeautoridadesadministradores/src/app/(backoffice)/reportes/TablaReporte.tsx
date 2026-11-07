import { formatear, Variacion, type FormatoKpi } from "@/components/Kpi";
import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";
import {
  Table,
  TableBody,
  TableCell,
  TableFooter,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { FilaReporte, TablaReporte as Tabla, ValorKpi } from "@/lib/reportes-mock";
import { AGRUPACIONES, KPIS, MEDIDAS, type Medida, type Reporte } from "@/lib/reportes-kpis";

const NUMERO = new Intl.NumberFormat("es-UY", { maximumFractionDigits: 1 });

// Signed change against the previous value; rates in percentage points
function Diferencia({ valor, formato, mejorSiBaja }: { valor: ValorKpi; formato: FormatoKpi; mejorSiBaja: boolean }) {
  if (valor.anterior === null) return <span className="text-muted-foreground">—</span>;

  const diferencia = valor.actual - valor.anterior;
  if (diferencia === 0) return <span className="text-muted-foreground">0</span>;

  const signo = diferencia > 0 ? "+" : "−";
  const texto =
    formato === "porcentaje"
      ? `${signo}${NUMERO.format(Math.abs(diferencia) * 100)} p.p.`
      : `${signo}${formatear(Math.abs(diferencia), formato)}`;
  const bueno = diferencia > 0 !== mejorSiBaja;

  return <span className={bueno ? "text-emerald-700" : "text-red-700"}>{texto}</span>;
}

function Celda({ fila, idKpi, medida }: {
  fila: FilaReporte;
  idKpi: Reporte["kpis"][number]["id"];
  medida: Medida;
}) {
  const valor = fila.valores[idKpi];
  const { formato, mejorSiBaja } = KPIS[idKpi];
  if (!valor) return <span className="text-muted-foreground">—</span>;

  switch (medida) {
    case "actual":
      return <>{formatear(valor.actual, formato)}</>;
    case "anterior":
      return valor.anterior === null
        ? <span className="text-muted-foreground">—</span>
        : <>{formatear(valor.anterior, formato)}</>;
    case "crecimiento":
      return valor.anterior === null
        ? <span className="text-muted-foreground">—</span>
        : <Variacion valor={valor.actual} comparativo={valor.anterior} formato={formato} mejorSiBaja={mejorSiBaja} />;
    case "diferencia":
      return <Diferencia valor={valor} formato={formato} mejorSiBaja={mejorSiBaja} />;
  }
}

function Fila({ fila, reporte, total = false }: { fila: FilaReporte; reporte: Reporte; total?: boolean }) {
  return (
    <TableRow className={total ? "bg-muted font-semibold hover:bg-muted" : ""}>
      <TableCell
        className={`sticky left-0 z-10 px-3 py-1.5 font-medium ${total ? "bg-muted" : "bg-card"}`}
      >
        {fila.etiqueta}
      </TableCell>
      {reporte.kpis.flatMap((kpi) =>
        kpi.medidas.map((medida, i) => (
          <TableCell
            key={`${kpi.id}-${medida}`}
            className={`px-3 py-1.5 text-right tabular-nums ${i === 0 ? "border-l" : ""}`}
          >
            <Celda fila={fila} idKpi={kpi.id} medida={medida} />
          </TableCell>
        ))
      )}
    </TableRow>
  );
}

export const FILAS_POR_PAGINA = 15;

type Props = {
  tabla: Tabla;
  reporte: Reporte;
  pagina: number;
  // Link to another page of the same report
  hrefPagina: (pagina: number) => string;
};

// One row per group; each KPI spans one column per measure it shows. Only the current page is drawn: the
// exports take every row from the data
export default function TablaReporte({ tabla, reporte, pagina, hrefPagina }: Props) {
  const paginas = Math.max(1, Math.ceil(tabla.filas.length / FILAS_POR_PAGINA));
  const paginaActual = Math.min(Math.max(pagina, 1), paginas);
  const desde = (paginaActual - 1) * FILAS_POR_PAGINA;
  const columnas = 1 + reporte.kpis.reduce((n, k) => n + k.medidas.length, 0);

  return (
    <div className="space-y-3">
      <div className="overflow-hidden rounded-xl border bg-card shadow-sm">
        <Table>
          <TableHeader className="bg-muted/70">
            <TableRow className="hover:bg-transparent">
              <TableHead
                rowSpan={2}
                className="sticky left-0 z-20 h-auto bg-muted px-3 py-1.5 align-bottom"
              >
                {AGRUPACIONES[reporte.agrupacion]}
              </TableHead>
              {reporte.kpis.map((kpi) => (
                <TableHead
                  key={kpi.id}
                  colSpan={kpi.medidas.length}
                  scope="colgroup"
                  className="h-auto border-l px-3 pt-1.5 pb-0.5 align-bottom font-semibold whitespace-normal"
                >
                  {/* Long names wrap downwards instead of widening the columns */}
                  <span className="block max-w-48 leading-snug">{KPIS[kpi.id].nombre}</span>
                </TableHead>
              ))}
            </TableRow>
            <TableRow className="hover:bg-transparent">
              {reporte.kpis.flatMap((kpi) =>
                kpi.medidas.map((medida, i) => (
                  <TableHead
                    key={`${kpi.id}-${medida}`}
                    className={`h-auto px-3 pt-0.5 pb-1.5 text-right text-xs text-muted-foreground ${i === 0 ? "border-l" : ""}`}
                  >
                    {MEDIDAS[medida]}
                  </TableHead>
                ))
              )}
            </TableRow>
          </TableHeader>

          <TableBody>
            {tabla.filas.length === 0 ? (
              <TableRow>
                <TableCell colSpan={columnas} className="py-10 text-center text-muted-foreground">
                  Sin datos para los filtros elegidos.
                </TableCell>
              </TableRow>
            ) : (
              tabla.filas
                .slice(desde, desde + FILAS_POR_PAGINA)
                .map((fila) => <Fila key={fila.clave} fila={fila} reporte={reporte} />)
            )}
          </TableBody>

          <TableFooter>
            <Fila fila={tabla.total} reporte={reporte} total />
          </TableFooter>
        </Table>
      </div>

      <Paginacion pagina={paginaActual} paginas={paginas} filas={tabla.filas.length} desde={desde} href={hrefPagina} />
    </div>
  );
}

function Paginacion({ pagina, paginas, filas, desde, href }: {
  pagina: number;
  paginas: number;
  filas: number;
  desde: number;
  href: (pagina: number) => string;
}) {
  if (paginas <= 1) return null;

  // "1 … 4 5 6 … 9"
  const numeros = Array.from({ length: paginas }, (_, i) => i + 1).filter(
    (n) => n === 1 || n === paginas || Math.abs(n - pagina) <= 1
  );

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 print:hidden">
      <p className="text-sm text-muted-foreground">
        {desde + 1}–{Math.min(desde + FILAS_POR_PAGINA, filas)} de {filas} filas
      </p>
      <Pagination className="mx-0 w-auto">
        <PaginationContent>
          <PaginationItem>
            <PaginationPrevious
              text="Anterior"
              href={href(Math.max(pagina - 1, 1))}
              aria-disabled={pagina === 1}
              className={pagina === 1 ? "pointer-events-none opacity-40" : ""}
            />
          </PaginationItem>
          {numeros.map((n, i) => (
            <PaginationItem key={n} className="flex items-center gap-1">
              {i > 0 && n - numeros[i - 1] > 1 && <PaginationEllipsis />}
              <PaginationLink href={href(n)} isActive={n === pagina}>
                {n}
              </PaginationLink>
            </PaginationItem>
          ))}
          <PaginationItem>
            <PaginationNext
              text="Siguiente"
              href={href(Math.min(pagina + 1, paginas))}
              aria-disabled={pagina === paginas}
              className={pagina === paginas ? "pointer-events-none opacity-40" : ""}
            />
          </PaginationItem>
        </PaginationContent>
      </Pagination>
    </div>
  );
}

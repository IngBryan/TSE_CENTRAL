import Link from "next/link";
import { redirect } from "next/navigation";
import { consultarIdentidad } from "@/lib/backoffice-sesion";
import { obtenerReglas, historialReglas } from "@/lib/reglas-fiscalizacion-backend";
import { cerrarSesionAction } from "@/app/login/actions";
import FormularioReglas from "./FormularioReglas";

function fecha(valor: string) {
  return new Date(valor).toLocaleString("es-UY", { timeZone: "America/Montevideo", dateStyle: "short", timeStyle: "short" });
}

export default async function ReglasFiscalizacionPage() {
  const identidad = await consultarIdentidad();
  if (identidad?.rol !== "ADMINISTRADOR") redirect("/login");
  const [resultado, historial] = await Promise.all([obtenerReglas(), historialReglas()]);
  return <main className="mx-auto w-full max-w-6xl space-y-6 px-6 py-10">
    <nav aria-label="Administración" className="flex gap-5 text-sm">
      <Link className="underline" href="/nodos-perifericos">Nodos periféricos</Link>
      <Link className="font-semibold" href="/reglas-fiscalizacion" aria-current="page">Reglas de fiscalización</Link>
    </nav>
    <header className="flex flex-wrap items-start justify-between gap-4">
      <div><h1 className="text-3xl font-semibold">Reglas de fiscalización</h1>
        <p className="mt-2 text-muted-foreground">Ajustá las tolerancias de carga y desvío de ruta para toda la plataforma.</p>
        <p className="mt-1 text-xs text-muted-foreground">Sesión: {identidad.ci} · Administrador</p>
      </div>
      <form action={cerrarSesionAction}><button className="rounded-md border px-4 py-2 text-sm hover:bg-muted">Cerrar sesión</button></form>
    </header>
    {!resultado.ok ? <div role="alert" className="rounded-lg border p-4 text-destructive">{resultado.mensaje}</div> : <>
      <div className="rounded-lg bg-muted p-4 text-sm">
        <p className="font-medium">Revisión {resultado.configuracion.revision} · {resultado.configuracion.publicada ? "Publicada" : "Distribución pendiente"}</p>
        <p className="mt-1">Actualizada el {fecha(resultado.configuracion.fechaActualizacion)} por {resultado.configuracion.usuario}.</p>
      </div>
      <FormularioReglas configuracion={resultado.configuracion} />
    </>}
    <section aria-labelledby="historial-titulo" className="space-y-3">
      <h2 id="historial-titulo" className="text-xl font-semibold">Últimas revisiones</h2>
      {historial === null ? <p role="status" className="text-sm text-muted-foreground">No se pudo cargar el historial.</p> :
        <div className="overflow-x-auto rounded-lg border"><table className="w-full text-left text-sm">
          <thead className="bg-muted"><tr>{["Revisión", "Fecha y autor", "Carga", "Desvío", "Fundamento"].map(t => <th key={t} scope="col" className="p-3">{t}</th>)}</tr></thead>
          <tbody>{historial.map(c => <tr key={c.revision} className="border-t">
            <td className="p-3">{c.revision}</td><td className="p-3">{fecha(c.fechaActualizacion)}<br />{c.usuario}</td>
            <td className="p-3">{c.toleranciaCargaPorcentaje} % / {c.toleranciaCargaKg} kg</td>
            <td className="p-3">{c.distanciaDesvioMetros} m / {c.ventanaDesvioSegundos} s</td>
            <td className="max-w-sm break-words p-3">{c.fundamento}</td>
          </tr>)}</tbody>
        </table></div>}
    </section>
  </main>;
}

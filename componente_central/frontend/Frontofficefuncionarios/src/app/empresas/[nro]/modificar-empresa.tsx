"use client";

import { useState, useTransition } from "react";
import { botonPrimario, botonSecundario } from "@/components/encabezado";
import { MAX_DIRECCION, MAX_NOMBRE, type EmpresaDetalle } from "@/lib/empresas";
import { modificarEmpresa } from "../actions";

const entrada =
  "h-10 rounded-lg border border-black/15 bg-transparent px-3 text-base font-normal text-black disabled:opacity-60 dark:border-white/20 dark:text-zinc-50";
const etiqueta = "flex flex-col gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300";

// FA3: el funcionario ve los datos actuales, los modifica y confirma. Las reglas (obligatorios, largo
// maximo) las valida el backend; aca solo se limita el largo para no mandar algo que va a rechazar.
// Lo abre AccionesEmpresa, que tambien muestra el mensaje cuando sale bien.
export function ModificarEmpresa({
  empresa,
  onCerrar,
  onModificada,
  onDesactualizada,
}: {
  empresa: EmpresaDetalle;
  onCerrar: () => void;
  onModificada: (mensaje: string) => void;
  // Otro funcionario la modifico mientras tanto: el formulario se cierra y la pagina muestra los datos actuales
  onDesactualizada: (aviso: string) => void;
}) {
  // La version con la que se abrio el formulario, aunque la pagina se vuelva a renderizar mientras tanto
  const [version] = useState(empresa.version ?? 0);
  const [datos, setDatos] = useState({
    nombrePublico: empresa.nombrePublico ?? "",
    razonSocial: empresa.razonSocial ?? "",
    direccion: empresa.direccion ?? "",
    habilitada: empresa.habilitada,
  });
  const [error, setError] = useState<string | null>(null);
  const [campos, setCampos] = useState<Record<string, string>>({});
  const [pendiente, iniciar] = useTransition();
  // Igual que el backend: sin responsable no se puede pasar a habilitada (si ya lo estaba, se puede deshabilitar)
  const sePuedeHabilitar = Boolean(empresa.responsable) || empresa.habilitada;

  const cambiar = <K extends keyof typeof datos>(campo: K, valor: (typeof datos)[K]) =>
    setDatos((d) => ({ ...d, [campo]: valor }));

  const enviar = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    iniciar(async () => {
      // Si sale bien, revalidatePath en la action vuelve a renderizar la pagina con los datos nuevos
      const r = await modificarEmpresa(empresa.nroEmpresa, { ...datos, version });
      if (r.ok) {
        onModificada(r.mensaje);
      } else if (r.desactualizada) {
        onDesactualizada(r.error);
      } else {
        setError(r.error);
        setCampos(r.campos ?? {});
      }
    });
  };

  const errorCampo = (n: string) =>
    campos[n] && <span className="text-xs font-normal text-red-700 dark:text-red-300">{campos[n]}</span>;

  return (
    <form onSubmit={enviar} className="flex flex-col gap-4 rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950">
      <h2 className="text-lg font-semibold text-black dark:text-zinc-50">Modificar la empresa</h2>
      <p className="text-sm text-zinc-600 dark:text-zinc-400">
        El número de empresa ({empresa.nroEmpresa}) no se puede modificar.
      </p>

      {error && (
        <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
          {error}
        </p>
      )}

      <label className={etiqueta}>
        Nombre público
        <input
          value={datos.nombrePublico}
          maxLength={MAX_NOMBRE}
          required
          disabled={pendiente}
          onChange={(e) => cambiar("nombrePublico", e.target.value)}
          className={entrada}
        />
        {errorCampo("nombrePublico")}
      </label>

      <label className={etiqueta}>
        Razón social
        <input
          value={datos.razonSocial}
          maxLength={MAX_NOMBRE}
          required
          disabled={pendiente}
          onChange={(e) => cambiar("razonSocial", e.target.value)}
          className={entrada}
        />
        {errorCampo("razonSocial")}
      </label>

      <label className={etiqueta}>
        Dirección
        <input
          value={datos.direccion}
          maxLength={MAX_DIRECCION}
          required
          disabled={pendiente}
          onChange={(e) => cambiar("direccion", e.target.value)}
          className={entrada}
        />
        {errorCampo("direccion")}
      </label>

      <label className="flex items-start gap-2 text-sm text-zinc-700 dark:text-zinc-300">
        <input
          type="checkbox"
          checked={datos.habilitada}
          disabled={pendiente || !sePuedeHabilitar}
          onChange={(e) => cambiar("habilitada", e.target.checked)}
          className="mt-0.5"
        />
        <span>
          <span className="font-medium">Empresa habilitada</span>
          <span className="block text-zinc-500">
            {sePuedeHabilitar
              ? "Solo una empresa habilitada puede dar de alta choferes."
              : "Para habilitarla, primero asociale un responsable."}
          </span>
          {errorCampo("habilitada")}
        </span>
      </label>

      <div className="flex gap-3">
        <button type="submit" disabled={pendiente} className={botonPrimario}>
          {pendiente ? "Guardando…" : "Guardar cambios"}
        </button>
        <button type="button" disabled={pendiente} onClick={onCerrar} className={botonSecundario}>
          Cancelar
        </button>
      </div>
    </form>
  );
}

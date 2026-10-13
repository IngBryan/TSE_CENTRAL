"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { botonGubUy, botonSecundario } from "@/components/encabezado";
import { asignarGuia, type ChoferResumen, type Guia, type VehiculoResumen } from "../../actions";

const nombre = (c: ChoferResumen) => [c.primerNombre, c.primerApellido].filter(Boolean).join(" ") || c.cedula;

// CU-07 pasos 3 a 9: elegir vehiculo, elegir chofer, ver las selecciones y confirmar (o cancelar, 8a).
export function AsignarViaje({ guia, vehiculos, choferes }: { guia: Guia; vehiculos: VehiculoResumen[]; choferes: ChoferResumen[] }) {
  const router = useRouter();
  const [matricula, setMatricula] = useState<string | null>(null);
  const [idChofer, setIdChofer] = useState<number | null>(null);
  const [resumen, setResumen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pendiente, iniciar] = useTransition();

  const vehiculo = vehiculos.find((v) => v.matricula === matricula);
  const chofer = choferes.find((c) => c.id === idChofer);

  const confirmar = () => {
    if (!vehiculo || !chofer) return;
    setError(null);
    iniciar(async () => {
      const r = await asignarGuia(guia.id, vehiculo.matricula, chofer.id);
      if (r.ok) {
        router.push(`/empresa/guias?asignada=${encodeURIComponent(guia.id)}`);
      } else {
        setError([r.error, ...Object.values(r.campos ?? {})].join(" "));
        setResumen(false);
      }
    });
  };

  const opcion = (activa: boolean) =>
    `flex cursor-pointer items-center gap-3 rounded-lg border p-3 ${
      activa ? "border-[#0068b3] bg-[#0068b3]/5" : "border-black/10 dark:border-white/15"
    }`;

  // Paso 7: el sistema muestra las selecciones elegidas
  if (resumen && vehiculo && chofer) {
    return (
      <div className="flex flex-col gap-5 rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950">
        <h2 className="text-lg font-semibold text-black dark:text-zinc-50">Confirmá la asignación</h2>
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-zinc-700 dark:text-zinc-300">
          <dt className="font-medium">Guía</dt>
          <dd>
            {guia.id} · {guia.origen} → {guia.destino}
          </dd>
          <dt className="font-medium">Vehículo</dt>
          <dd>
            {vehiculo.matricula} · {vehiculo.marca} {vehiculo.modelo}
          </dd>
          <dt className="font-medium">Chofer</dt>
          <dd>
            {nombre(chofer)} (C.I. {chofer.cedula})
          </dd>
        </dl>
        {vehiculo.capacidadCargaKg < guia.pesoCargaKg && (
          <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900 dark:bg-amber-950 dark:text-amber-200">
            Atención: la carga ({guia.pesoCargaKg.toLocaleString("es-UY")} kg) supera la capacidad del vehículo (
            {vehiculo.capacidadCargaKg.toLocaleString("es-UY")} kg).
          </p>
        )}
        <div className="flex gap-3">
          <button type="button" onClick={confirmar} disabled={pendiente} className={botonGubUy}>
            {pendiente ? "Asignando…" : "Confirmar asignación"}
          </button>
          {/* 8a: cancela y vuelve al listado de guias */}
          <Link href="/empresa/guias" className={botonSecundario}>
            Cancelar
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      {error && (
        <p role="alert" className="rounded-lg bg-red-50 p-4 text-red-800 dark:bg-red-950 dark:text-red-200">
          {error}
        </p>
      )}

      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-lg font-semibold text-black dark:text-zinc-50">1. Elegí el vehículo</legend>
        {vehiculos.map((v) => (
          <label key={v.matricula} className={opcion(matricula === v.matricula)}>
            <input type="radio" name="vehiculo" aria-label={`${v.matricula} ${v.marca} ${v.modelo}`} checked={matricula === v.matricula} onChange={() => setMatricula(v.matricula)} />
            <span className="flex-1">
              <strong>{v.matricula}</strong> · {v.marca} {v.modelo}
            </span>
            <span className="text-sm tabular-nums text-zinc-500">hasta {v.capacidadCargaKg.toLocaleString("es-UY")} kg</span>
          </label>
        ))}
      </fieldset>

      <fieldset className="flex flex-col gap-2" disabled={!matricula}>
        <legend className={`mb-2 text-lg font-semibold ${matricula ? "text-black dark:text-zinc-50" : "text-zinc-400"}`}>
          2. Elegí el chofer
        </legend>
        {choferes.map((c) => (
          <label key={c.id} className={`${opcion(idChofer === c.id)} ${matricula ? "" : "opacity-50"}`}>
            <input type="radio" name="chofer" aria-label={nombre(c)} checked={idChofer === c.id} onChange={() => setIdChofer(c.id)} />
            <span className="flex-1">{nombre(c)}</span>
            <span className="text-sm tabular-nums text-zinc-500">C.I. {c.cedula}</span>
          </label>
        ))}
      </fieldset>

      <div className="flex gap-3">
        <button type="button" onClick={() => setResumen(true)} disabled={!vehiculo || !chofer} className={botonGubUy}>
          Continuar
        </button>
        <Link href="/empresa/guias" className={botonSecundario}>
          Cancelar
        </Link>
      </div>
    </div>
  );
}

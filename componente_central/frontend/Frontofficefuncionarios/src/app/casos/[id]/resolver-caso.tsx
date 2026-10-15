"use client";

import { useRouter } from "next/navigation";
import { useState, useTransition } from "react";
import { botonGubUy, botonSecundario } from "@/components/encabezado";
import type { Decision } from "@/lib/casos";
import { resolverCaso } from "../actions";

// RegistroAuditoriaCaso.MAX_FUNDAMENTO en el backend
const MAX_FUNDAMENTO = 2000;

const OPCIONES: { valor: Decision; texto: string; ayuda: string }[] = [
  { valor: "CONFIRMAR", texto: "Confirmar", ayuda: "La infracción queda confirmada." },
  { valor: "DESESTIMAR", texto: "Desestimar", ayuda: "Se acepta el descargo o la evidencia no alcanza." },
];

// FA5: el funcionario confirma o desestima el caso con un fundamento obligatorio. Se pide confirmar
// antes de enviar porque la resolucion es definitiva (CONFIRMADA y DESESTIMADA son estados finales).
export function ResolverCaso({ idCaso, sinDescargo }: { idCaso: number; sinDescargo: boolean }) {
  const router = useRouter();
  const [decision, setDecision] = useState<Decision | null>(null);
  const [fundamento, setFundamento] = useState("");
  const [confirmar, setConfirmar] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [campos, setCampos] = useState<Record<string, string>>({});
  const [pendiente, iniciar] = useTransition();

  const completo = decision != null && fundamento.trim() !== "";

  const enviar = () => {
    if (!decision) return;
    setError(null);
    iniciar(async () => {
      const r = await resolverCaso(idCaso, decision, fundamento);
      if (r.ok) {
        router.refresh();
      } else {
        setError(r.error);
        setCampos(r.campos ?? {});
        setConfirmar(false);
      }
    });
  };

  const errorCampo = (n: string) =>
    campos[n] && <span className="text-xs font-normal text-red-700 dark:text-red-300">{campos[n]}</span>;

  return (
    <section className="flex flex-col gap-4 rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950">
      <h2 className="text-lg font-semibold text-black dark:text-zinc-50">Resolver el caso</h2>
      {sinDescargo && (
        <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900 dark:bg-amber-950 dark:text-amber-200">
          La empresa no presentó descargo. Igual podés resolver el caso con la evidencia disponible.
        </p>
      )}

      {error && (
        <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-800 dark:bg-red-950 dark:text-red-200">
          {error}
        </p>
      )}

      <fieldset disabled={pendiente || confirmar} className="flex flex-col gap-2">
        <legend className="mb-2 text-sm font-medium text-zinc-700 dark:text-zinc-300">Decisión</legend>
        <div className="grid gap-3 sm:grid-cols-2">
          {OPCIONES.map((o) => (
            <label
              key={o.valor}
              className={`flex cursor-pointer flex-col gap-1 rounded-lg border p-3 text-sm ${
                decision === o.valor
                  ? "border-[#0068b3] bg-[#0068b3]/5"
                  : "border-black/15 dark:border-white/20"
              }`}
            >
              <span className="flex items-center gap-2 font-medium text-black dark:text-zinc-50">
                <input
                  type="radio"
                  name="decision"
                  value={o.valor}
                  checked={decision === o.valor}
                  onChange={() => setDecision(o.valor)}
                />
                {o.texto}
              </span>
              <span className="text-zinc-600 dark:text-zinc-400">{o.ayuda}</span>
            </label>
          ))}
        </div>
        {errorCampo("decision")}
      </fieldset>

      <label className="flex flex-col gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300">
        Fundamento (obligatorio)
        <textarea
          value={fundamento}
          maxLength={MAX_FUNDAMENTO}
          rows={4}
          disabled={pendiente || confirmar}
          onChange={(e) => setFundamento(e.target.value)}
          className="rounded-lg border border-black/15 bg-transparent p-3 text-base font-normal text-black dark:border-white/20 dark:text-zinc-50"
        />
        <span className="self-end text-xs font-normal text-zinc-500">
          {fundamento.length}/{MAX_FUNDAMENTO}
        </span>
        {errorCampo("fundamento")}
      </label>

      {!confirmar ? (
        <button
          type="button"
          disabled={!completo || pendiente}
          onClick={() => setConfirmar(true)}
          className={`${botonGubUy} self-start`}
        >
          Registrar resolución
        </button>
      ) : (
        <div className="flex flex-col gap-3 rounded-lg border border-black/10 p-4 dark:border-white/15">
          <p className="text-sm text-zinc-700 dark:text-zinc-300">
            ¿{decision === "CONFIRMAR" ? "Confirmar" : "Desestimar"} el caso N° {idCaso}? La resolución es definitiva y
            queda registrada en la auditoría con tu cédula y el fundamento.
          </p>
          <div className="flex gap-3">
            <button type="button" disabled={pendiente} onClick={enviar} className={botonGubUy}>
              {pendiente ? "Registrando…" : "Confirmar"}
            </button>
            <button type="button" disabled={pendiente} onClick={() => setConfirmar(false)} className={botonSecundario}>
              Volver
            </button>
          </div>
        </div>
      )}
    </section>
  );
}

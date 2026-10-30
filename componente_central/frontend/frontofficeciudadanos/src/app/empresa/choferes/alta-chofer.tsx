"use client";

import { useState, useTransition } from "react";
import { botonGubUy, botonSecundario } from "@/components/estilos";
import { confirmarChofer, consultarChofer, type RespuestaAlta } from "./actions";

type Aviso = { tipo: "ok" | "info" | "error"; texto: string };

const ESTILO_AVISO: Record<Aviso["tipo"], string> = {
  ok: "bg-green-50 text-green-900 dark:bg-green-950 dark:text-green-200",
  info: "bg-amber-50 text-amber-900 dark:bg-amber-950 dark:text-amber-200",
  error: "bg-red-50 text-red-800 dark:bg-red-950 dark:text-red-200",
};

function avisoDe(r: RespuestaAlta): Aviso | null {
  if (!r.ok) {
    switch (r.error) {
      case "cedula_invalida":
        return { tipo: "error", texto: "Ingresá una cédula válida (de 6 a 8 dígitos, sin puntos ni guion)." };
      case "no_autorizado":
        return { tipo: "error", texto: `No podés dar de alta choferes: ${r.detalle ?? "no sos responsable de la empresa"}.` };
      case "sin_sesion":
        return { tipo: "error", texto: "Tu sesión venció. Volvé a ingresar con gub.uy." };
      default:
        return { tipo: "error", texto: "El servicio no está disponible en este momento. Probá de nuevo en unos minutos." };
    }
  }
  const nombre = [r.primerNombre, r.primerApellido].filter(Boolean).join(" ");
  switch (r.resultado) {
    case "YA_ES_CHOFER": // FA1
      return { tipo: "info", texto: `La persona con cédula ${r.cedula} ya es chofer de la empresa.` };
    case "NO_EXISTE": // FA4
      return {
        tipo: "error",
        texto: `No se pudo validar la cédula ${r.cedula}: no existe en el registro de DNIC. No se la asoció a la empresa.`,
      };
    case "MENOR_DE_EDAD": // FA4
      return {
        tipo: "error",
        texto: `La persona con cédula ${r.cedula} no cumple con la edad mínima (18 años). No se la asoció a la empresa.`,
      };
    case "ASOCIADO_PENDIENTE": // FA5
      return {
        tipo: "info",
        texto: `No pudimos validar al chofer con cédula ${r.cedula} por ahora. Quedó asociado a la empresa pendiente de validación: mientras tanto no se lo puede asignar a viajes. Te avisaremos cuando se valide.`,
      };
    case "ALTA_CONFIRMADA": // paso 10
      return { tipo: "ok", texto: `Listo: ${nombre} es chofer de la empresa. Ya se lo puede asignar a viajes.` };
    case "DEBE_CONSULTAR":
      return { tipo: "error", texto: "No se pudo confirmar el alta. Volvé a ingresar la cédula." };
    default:
      return null;
  }
}

// CU-02: ingresar cedula -> confirmar el nombre (Confirmar / Cancelar) -> resultado.
export function AltaChofer() {
  const [abierto, setAbierto] = useState(false);
  const [cedula, setCedula] = useState("");
  const [aConfirmar, setAConfirmar] = useState<{ cedula: string; nombre: string } | null>(null);
  const [aviso, setAviso] = useState<Aviso | null>(null);
  const [pendiente, iniciar] = useTransition();

  const consultar = (e: React.FormEvent) => {
    e.preventDefault();
    setAviso(null);
    iniciar(async () => {
      const r = await consultarChofer(cedula);
      if (r.ok && r.resultado === "CONFIRMAR") {
        // Paso 7: solo primer nombre y primer apellido (Ley 18.331)
        setAConfirmar({ cedula: r.cedula, nombre: [r.primerNombre, r.primerApellido].filter(Boolean).join(" ") });
      } else {
        setAviso(avisoDe(r));
      }
    });
  };

  const confirmar = () => {
    if (!aConfirmar) return;
    iniciar(async () => {
      const r = await confirmarChofer(aConfirmar.cedula);
      setAConfirmar(null);
      setCedula("");
      setAviso(avisoDe(r));
    });
  };

  // FA6: no reconoce el nombre, vuelve a ingresar la cedula
  const cancelar = () => {
    setAConfirmar(null);
    setCedula("");
    setAviso({ tipo: "info", texto: "Cancelaste el alta. Podés ingresar otra cédula." });
  };

  if (!abierto) {
    return (
      <button type="button" onClick={() => setAbierto(true)} className={`${botonGubUy} self-start`}>
        Agregar chofer
      </button>
    );
  }

  return (
    <div className="flex flex-col gap-4 rounded-2xl bg-white p-6 shadow-sm dark:bg-zinc-950">
      <h2 className="text-lg font-semibold text-black dark:text-zinc-50">Agregar chofer</h2>

      {aviso && (
        <p role={aviso.tipo === "error" ? "alert" : "status"} className={`rounded-lg p-4 ${ESTILO_AVISO[aviso.tipo]}`}>
          {aviso.texto}
        </p>
      )}

      {aConfirmar ? (
        <div className="flex flex-col gap-4">
          <p className="text-zinc-700 dark:text-zinc-300">
            La cédula <strong>{aConfirmar.cedula}</strong> corresponde a <strong>{aConfirmar.nombre}</strong>. ¿Es la
            persona que querés dar de alta como chofer?
          </p>
          <div className="flex gap-3">
            <button type="button" onClick={confirmar} disabled={pendiente} className={botonGubUy}>
              {pendiente ? "Confirmando…" : "Confirmar"}
            </button>
            <button type="button" onClick={cancelar} disabled={pendiente} className={botonSecundario}>
              Cancelar
            </button>
          </div>
        </div>
      ) : (
        <form onSubmit={consultar} className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <label className="flex flex-1 flex-col gap-1 text-sm font-medium text-zinc-700 dark:text-zinc-300">
            Cédula del chofer
            <input
              name="cedula"
              inputMode="numeric"
              autoComplete="off"
              placeholder="12345678"
              value={cedula}
              onChange={(e) => setCedula(e.target.value)}
              required
              className="h-10 rounded-lg border border-black/15 bg-transparent px-3 text-base dark:border-white/20"
            />
          </label>
          <button type="submit" disabled={pendiente} className={botonGubUy}>
            {pendiente ? "Consultando…" : "Buscar"}
          </button>
        </form>
      )}
    </div>
  );
}

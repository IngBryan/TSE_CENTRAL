import test from "node:test";
import assert from "node:assert/strict";
import { desdeSeguimientoPosterior, primeraConfirmacionGps, referenciasDePrueba, soloPosteriores } from "./evidencia-desvio.ts";

const t = iso => iso;

test("el seguimiento empieza DESPUES de la deteccion", () => {
  assert.equal(desdeSeguimientoPosterior(t("2026-11-04T00:00:00.000Z"), t("2026-11-04T01:00:00.000Z")),
    "2026-11-04T01:00:00.001Z");
});

test("si la asignacion es posterior, no consulta posiciones de otro viaje", () => {
  assert.equal(desdeSeguimientoPosterior(t("2026-11-04T05:00:00Z"), t("2026-11-04T01:00:00Z")),
    "2026-11-04T05:00:00.000Z");
});

test("sin fecha de deteccion valida no se construye seguimiento historico", () => {
  assert.equal(desdeSeguimientoPosterior("2026-11-04T00:00:00Z", null), null);
  assert.equal(desdeSeguimientoPosterior(null, "2026-11-04T01:00:00Z"), null);
});

test("posiciones anteriores y coincidentes nunca integran la trayectoria posterior", () => {
  const puntos = [
    { fechaHora: "2026-11-04T01:00:00.000Z", id: "antes" },
    { fechaHora: "2026-11-04T01:00:10.000Z", id: "posterior" },
    { fechaHora: "2026-11-04T00:59:50.000Z", id: "previo" },
  ];
  assert.deepEqual(soloPosteriores(puntos, "2026-11-04T01:00:00.000Z").map(p => p.id), ["posterior"]);
  assert.deepEqual(soloPosteriores(puntos, null), []);
});

test("referencias históricas dependen de hashes guardados, no de rutas actuales", () => {
  const prueba = { hashesRutas: ["hash-a", "hash-b"] };
  const archivadas = [{ sha256: "hash-b", idRuta: 22 }, { sha256: "hash-a", idRuta: 11 },
    { sha256: "hash-vigente-ajena", idRuta: 99 }];
  assert.deepEqual(referenciasDePrueba(prueba, archivadas), {
    rutas: [{ sha256: "hash-a", idRuta: 11 }, { sha256: "hash-b", idRuta: 22 }], faltantes: 0,
  });
});

test("nunca sustituir una ruta histórica ausente con la ruta vigente", () => {
  const salida = referenciasDePrueba({ hashesRutas: ["historico"] },
    [{ sha256: "actual", idRuta: 99 }]);
  assert.deepEqual(salida.rutas, []);
  assert.equal(salida.faltantes, 1);
});

test("sin duplicar hashes o inventar referencias", () => {
  assert.deepEqual(referenciasDePrueba({ hashesRutas: ["x", "x", "y"] }, [{ sha256: "x" }]),
    { rutas: [{ sha256: "x" }], faltantes: 1 });
  assert.deepEqual(referenciasDePrueba(null, [{ sha256: "x" }]), { rutas: [], faltantes: 0 });
});


test("VM en pausa: el inicio operativo es la PRIMERA confirmación GPS y no la fecha administrativa", () => {
  const archivadas = [
    { fechaHora: "2026-11-02T14:59:00Z" },
    { fechaHora: "2026-11-02T14:35:00Z" },
    { fechaHora: "fecha-invalida" },
  ];
  const deteccionAdministrativa = "2026-11-02T15:38:00Z";
  const referencia = primeraConfirmacionGps(archivadas);
  assert.equal(referencia, "2026-11-02T14:35:00.000Z");
  const recorridoCompleto = [
    { fechaHora: "2026-11-02T14:34:00Z", id: "antes" },
    { fechaHora: "2026-11-02T14:35:00Z", id: "confirmacion" },
    { fechaHora: "2026-11-02T14:45:00Z", id: "despues_gps_pero_antes_admin" },
    { fechaHora: "2026-11-02T16:00:00Z", id: "despues_admin" },
  ];
  assert.deepEqual(soloPosteriores(recorridoCompleto, referencia).map(p => p.id),
    ["despues_gps_pero_antes_admin", "despues_admin"]);
  assert.equal(recorridoCompleto.length, 4, "El recorrido completo NO se recorta");
  assert.deepEqual(soloPosteriores(recorridoCompleto, deteccionAdministrativa).map(p => p.id),
    ["despues_admin"], "Demuestra por qué el filtro administrativo omitía GPS históricos");
  assert.deepEqual(archivadas[0], { fechaHora: "2026-11-02T14:59:00Z" },
    "La lista de confirmaciones archivadas no se muta ni reordena");
});

test("sin confirmaciones GPS válidas no se inventa una referencia de seguimiento", () => {
  assert.equal(primeraConfirmacionGps([]), null);
  assert.equal(primeraConfirmacionGps([{ fechaHora: "invalida" }]), null);
  assert.deepEqual(soloPosteriores([{ fechaHora: "2026-11-02T16:00:00Z" }], null), []);
});

test("una confirmación nueva más antigua redefine de modo explícito la referencia operativa", () => {
  const anteriores = [{ fechaHora: "2026-11-02T15:00:00Z" }];
  const nuevas = [...anteriores, { fechaHora: "2026-11-02T14:00:00Z" }];
  assert.equal(primeraConfirmacionGps(anteriores), "2026-11-02T15:00:00.000Z");
  assert.equal(primeraConfirmacionGps(nuevas), "2026-11-02T14:00:00.000Z");
});

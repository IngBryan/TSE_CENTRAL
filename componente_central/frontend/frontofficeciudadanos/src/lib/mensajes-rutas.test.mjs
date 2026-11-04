import test from "node:test";
import assert from "node:assert/strict";
import { mensajeErrorRutas } from "./mensajes-rutas.ts";

test("el error por localidades distintas es comprensible", () => {
  assert.match(mensajeErrorRutas(400, { error: "localidades_invalidas" }), /diferentes/);
});

test("una previsualización vencida invita a repetir la búsqueda", () => {
  assert.match(mensajeErrorRutas(409, { error: "rutas_actualizadas" }), /Volvé a buscarlas/);
});

test("problemas del servicio de rutas no muestran detalles internos", () => {
  const texto = mensajeErrorRutas(503, {
    error: "osrm_no_disponible", detalle: "OSRM devolvio HTTP 503 en api.internal",
  });
  assert.doesNotMatch(texto, /OSRM|HTTP|api\.internal|backend/i);
  assert.match(texto, /Intentá nuevamente/);
});

test("un error desconocido tampoco expone detalles del servidor", () => {
  const texto = mensajeErrorRutas(500, { error: "interno", detalle: "sha256=deadbeef" });
  assert.doesNotMatch(texto, /sha256|deadbeef|backend|API/);
});

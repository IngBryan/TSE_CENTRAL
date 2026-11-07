import test from "node:test";
import assert from "node:assert/strict";
import { habilitaPollingGps } from "./polling-gps.ts";

test("GPS en vivo: ASIGNADA inicia polling con fecha de asignación válida", () => {
  assert.equal(habilitaPollingGps("ASIGNADA", Date.parse("2026-11-04T03:48:09Z")), true);
});

test("GPS en vivo: EN_CURSO mantiene polling", () => {
  assert.equal(habilitaPollingGps("EN_CURSO", Date.now()), true);
});

test("GPS histórico: FINALIZADA no realiza polling", () => {
  assert.equal(habilitaPollingGps("FINALIZADA", Date.now()), false);
});

test("Una fecha inexistente no activa un polling con rango inválido", () => {
  for (const estado of ["ASIGNADA", "EN_CURSO", "FINALIZADA"]) {
    assert.equal(habilitaPollingGps(estado, NaN), false);
  }
});

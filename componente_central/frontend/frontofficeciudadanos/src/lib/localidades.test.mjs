import test from 'node:test';
import assert from 'node:assert/strict';
import { localidadDeGuia, mostrarLocalidad } from './localidades.ts';

const localidades = [
  { id: 1, nombre: 'Mercedes', departamento: 'Soriano' },
  { id: 2, nombre: 'Nueva Palmira', departamento: 'Colonia' },
  { id: 3, nombre: 'San José', departamento: 'San José' },
  { id: 4, nombre: 'San José', departamento: 'Colonia' },
];

test('la identidad por ID tiene prioridad sobre nombres iguales', () => {
  assert.equal(localidadDeGuia(localidades, 4, 'San José')?.departamento, 'Colonia');
});

test('guías históricas: nombre único se puede resolver sin mutar datos', () => {
  assert.equal(localidadDeGuia(localidades, null, ' nueva   palmira ')?.id, 2);
});

test('guías históricas: no se infiere una localidad ambigua o no existente', () => {
  assert.equal(localidadDeGuia(localidades, null, 'San Jose'), undefined);
  assert.equal(localidadDeGuia(localidades, null, 'localidad inexistente'), undefined);
});

test('la presentación incluye departamento cuando existe', () => {
  assert.equal(mostrarLocalidad('San José', 'Colonia'), 'San José (Colonia)');
  assert.equal(mostrarLocalidad('Montevideo'), 'Montevideo');
});

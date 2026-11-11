import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeBusinessCursor, encodeBusinessCursor, parsePageSize } from '../src/pagination.js';

const row = {
  id: 'b7c8f2a1-8a27-4a99-9d7e-9ca1d0c457bf',
  cursor_created_at: '2026-11-10 08:30:12.123456+00'
};

test('el tamaño de página aplica un valor conservador por defecto y un máximo', () => {
  assert.equal(parsePageSize(null), 24);
  assert.equal(parsePageSize('12'), 12);
  assert.throws(() => parsePageSize('0'), { code: 'INVALID_PAGE_SIZE' });
  assert.throws(() => parsePageSize('500'), { code: 'INVALID_PAGE_SIZE' });
  assert.throws(() => parsePageSize('abc'), { code: 'INVALID_PAGE_SIZE' });
});

test('el cursor conserva la precisión de microsegundos de PostgreSQL', () => {
  const encoded = encodeBusinessCursor(row);
  const decoded = decodeBusinessCursor(encoded);
  assert.deepEqual(decoded, {
    createdAt: '2026-11-10 08:30:12.123456+00',
    id: row.id
  });
});

test('el cursor vacío representa la primera página', () => {
  assert.equal(decodeBusinessCursor(null), null);
  assert.equal(decodeBusinessCursor(''), null);
});

test('el cursor corrupto o manipulado se rechaza', () => {
  assert.throws(() => decodeBusinessCursor('not-a-cursor!'), { code: 'INVALID_CURSOR' });
  const invalid = Buffer.from(JSON.stringify({ createdAt: 'ayer', id: 'negocio' })).toString('base64url');
  assert.throws(() => decodeBusinessCursor(invalid), { code: 'INVALID_CURSOR' });
});

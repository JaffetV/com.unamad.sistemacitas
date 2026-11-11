import test from 'node:test';
import assert from 'node:assert/strict';
import { createAccessToken, hashPassword, verifyAccessToken, verifyPassword } from '../src/security.js';

const secret = 'test-secret-which-is-long-enough-for-hmac-256';

test('contraseñas usan hash y verificación segura', async () => {
  const encoded = await hashPassword('mi-clave-super-segura');
  assert.notEqual(encoded, 'mi-clave-super-segura');
  assert.equal(await verifyPassword('mi-clave-super-segura', encoded), true);
  assert.equal(await verifyPassword('otra-clave', encoded), false);
});

test('token firmado contiene identidad y rechaza alteraciones', () => {
  const token = createAccessToken({ id: 'u-1', email: 'a@example.com' }, secret, 60);
  assert.deepEqual(verifyAccessToken(token, secret), { id: 'u-1', email: 'a@example.com' });
  const [header, payload, signature] = token.split('.');
  const firstSignatureCharacter = signature[0];
  const alteredSignature = `${firstSignatureCharacter === 'A' ? 'B' : 'A'}${signature.slice(1)}`;
  const alteredToken = `${header}.${payload}.${alteredSignature}`;
  assert.throws(() => verifyAccessToken(alteredToken, secret));
  assert.throws(() => verifyAccessToken(createAccessToken({ id: 'u-1' }, secret, -5), secret));
});

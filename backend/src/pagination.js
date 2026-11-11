import { HttpError } from './domain.js';

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const CURSOR_PATTERN = /^[A-Za-z0-9_-]{1,512}$/;

export function parsePageSize(value, defaultValue = 24, maximum = 50) {
  if (value == null || value === '') return defaultValue;
  const parsed = Number(value);
  if (!Number.isInteger(parsed) || parsed < 1 || parsed > maximum) {
    throw new HttpError(400, `El límite debe ser un entero entre 1 y ${maximum}.`, 'INVALID_PAGE_SIZE');
  }
  return parsed;
}

export function encodeBusinessCursor(row) {
  // Usa el texto que entrega PostgreSQL para conservar microsegundos; Date de JS
  // reduce la precisión a milisegundos y podría repetir filas entre páginas.
  const createdAt = String(row?.cursor_created_at ?? row?.created_at ?? '');
  const id = String(row?.id ?? '');
  if (!Number.isFinite(Date.parse(createdAt)) || !UUID_PATTERN.test(id)) {
    throw new TypeError('No se puede crear un cursor con datos inválidos.');
  }
  return Buffer.from(JSON.stringify({ createdAt, id }), 'utf8').toString('base64url');
}

export function decodeBusinessCursor(value) {
  if (value == null || value === '') return null;
  if (typeof value !== 'string' || !CURSOR_PATTERN.test(value)) {
    throw new HttpError(400, 'El cursor de paginación no es válido.', 'INVALID_CURSOR');
  }
  try {
    const decoded = JSON.parse(Buffer.from(value, 'base64url').toString('utf8'));
    const createdAt = new Date(decoded?.createdAt);
    const id = String(decoded?.id ?? '');
    if (!Number.isFinite(createdAt.getTime()) || !UUID_PATTERN.test(id)) throw new Error('invalid cursor');
    return { createdAt: String(decoded.createdAt), id };
  } catch {
    throw new HttpError(400, 'El cursor de paginación no es válido.', 'INVALID_CURSOR');
  }
}

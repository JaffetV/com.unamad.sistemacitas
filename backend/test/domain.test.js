import test from 'node:test';
import assert from 'node:assert/strict';
import {
  HttpError, assertBusinessHoursConfigured, canTransitionReservation, normalizeEmail,
  assertReservationDateFuture, validateAvailabilityDate, validateBusinessPayload, validateCustomerPayload, validateIdempotencyKey,
  validateListingPayload, validateReservationPayload
} from '../src/domain.js';

const validBusiness = {
  name: 'Barbería Centro', categoryId: 'a8c43d28-0e84-4b57-954c-0e81e3b38a6c',
  phone: '999888777', department: 'Madre de Dios', province: 'Tambopata', district: 'Puerto Maldonado',
  address: 'Av. Principal 123', modes: ['CITA','RECOJO']
};

test('normaliza correo y rechaza formato inválido', () => {
  assert.equal(normalizeEmail('  CLIENTE@EXAMPLE.COM '), 'cliente@example.com');
  assert.throws(() => normalizeEmail('correo-sin-dominio'), HttpError);
});

test('cliente necesita datos mínimos y contraseña robusta', () => {
  const payload = validateCustomerPayload({ fullName: 'Ana Pérez', email: 'ana@example.com', password: 'clave-segura-123' });
  assert.equal(payload.fullName, 'Ana Pérez');
  assert.equal(payload.email, 'ana@example.com');
  assert.throws(() => validateCustomerPayload({ fullName: 'Ana', email: 'ana@example.com', password: '123' }), HttpError);
});

test('negocio admite una o varias modalidades conocidas', () => {
  assert.deepEqual(validateBusinessPayload(validBusiness).modes, ['CITA','RECOJO']);
  assert.throws(() => validateBusinessPayload({ ...validBusiness, modes: ['DELIVERY'] }), HttpError);
});

test('publicación valida tipo, modalidad, precio y stock', () => {
  const service = validateListingPayload({ title: 'Corte clásico', kind: 'SERVICIO', mode: 'CITA', price: 25, durationMinutes: 30 });
  assert.equal(service.durationMinutes, 30);
  assert.throws(() => validateListingPayload({ title: 'Sándwich', kind: 'PRODUCTO', mode: 'AMBOS', price: 10 }), HttpError);
  assert.throws(() => validateListingPayload({ title: 'Producto', kind: 'PRODUCTO', mode: 'RECOJO', price: 10, stock: -1 }), HttpError);
});

test('reserva requiere fecha futura, cantidad y canal válidos', () => {
  const payload = validateReservationPayload({ mode: 'CITA', quantity: 1, paymentMethod: 'EN_LOCAL', dateTime: '2099-01-01T10:00:00-05:00' });
  assert.equal(payload.mode, 'CITA');
  assert.throws(() => validateReservationPayload({ mode: 'CITA', quantity: 0, paymentMethod: 'EN_LOCAL', dateTime: '2099-01-01T10:00:00-05:00' }), HttpError);
  assert.throws(() => validateReservationPayload({ mode: 'CITA', quantity: 1, paymentMethod: 'EN_LOCAL', dateTime: '2099-01-01T10:00:00' }), HttpError);
});

test('una repetición idempotente puede parsear su fecha original aunque ya haya pasado', () => {
  const historic = validateReservationPayload({
    mode: 'CITA', quantity: 1, paymentMethod: 'EN_LOCAL', dateTime: '2020-01-01T10:00:00-05:00'
  }, { allowPast: true });
  assert.equal(historic.date.toISOString(), '2020-01-01T15:00:00.000Z');
  assert.throws(() => assertReservationDateFuture(historic.date), HttpError);
});

test('transiciones de reserva siguen el ciclo permitido', () => {
  assert.equal(canTransitionReservation('SOLICITADA','CONFIRMADA'), true);
  assert.equal(canTransitionReservation('CONFIRMADA','LISTA'), true);
  assert.equal(canTransitionReservation('COMPLETADA','CANCELADA'), false);
  assert.equal(canTransitionReservation('SOLICITADA','COMPLETADA'), false);
});

test('una reserva requiere que el negocio tenga al menos un horario activo', () => {
  assert.equal(assertBusinessHoursConfigured(2), 2);
  assert.equal(assertBusinessHoursConfigured('1'), 1);
  assert.throws(
    () => assertBusinessHoursConfigured(0),
    (error) => error instanceof HttpError && error.status === 409 && error.code === 'BUSINESS_HOURS_NOT_CONFIGURED'
  );
  assert.throws(() => assertBusinessHoursConfigured(null), HttpError);
});


test('clave de idempotencia exige formato seguro y longitud válida', () => {
  assert.equal(validateIdempotencyKey('android:booking-123'), 'android:booking-123');
  assert.throws(() => validateIdempotencyKey('short'), HttpError);
  assert.throws(() => validateIdempotencyKey('invalid key with spaces'), HttpError);
});

test('fecha de disponibilidad usa formato local AAAA-MM-DD y valida el calendario', () => {
  assert.equal(validateAvailabilityDate('2027-02-28'), '2027-02-28');
  assert.throws(() => validateAvailabilityDate('2027-02-30'), HttpError);
  assert.throws(() => validateAvailabilityDate('28/02/2027'), HttpError);
});

test('reserva admite asignación opcional a trabajador o recurso', () => {
  const payload = validateReservationPayload({
    mode: 'CITA', quantity: 1, paymentMethod: 'EN_LOCAL',
    dateTime: '2099-01-01T10:00:00-05:00', staffId: 'f24092b6-741d-4e20-b4a8-f08b97f0f98f'
  });
  assert.equal(payload.staffId, 'f24092b6-741d-4e20-b4a8-f08b97f0f98f');
  assert.equal(payload.resourceId, null);
  assert.throws(() => validateReservationPayload({
    mode: 'CITA', quantity: 1, paymentMethod: 'EN_LOCAL',
    dateTime: '2099-01-01T10:00:00-05:00',
    staffId: 'f24092b6-741d-4e20-b4a8-f08b97f0f98f',
    resourceId: 'a24092b6-741d-4e20-b4a8-f08b97f0f98f'
  }), HttpError);
  assert.throws(() => validateReservationPayload({
    mode: 'RECOJO', quantity: 1, paymentMethod: 'EN_LOCAL',
    dateTime: '2099-01-01T10:00:00-05:00',
    staffId: 'f24092b6-741d-4e20-b4a8-f08b97f0f98f'
  }), HttpError);
});

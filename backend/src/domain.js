export class HttpError extends Error {
  constructor(status, message, code = 'REQUEST_ERROR') {
    super(message);
    this.name = 'HttpError';
    this.status = status;
    this.code = code;
  }
}

export function requiredString(value, field, max = 200) {
  if (typeof value !== 'string' || !value.trim()) {
    throw new HttpError(400, `El campo ${field} es obligatorio.`, 'VALIDATION_ERROR');
  }
  const result = value.trim();
  if (result.length > max) {
    throw new HttpError(400, `El campo ${field} supera ${max} caracteres.`, 'VALIDATION_ERROR');
  }
  return result;
}

export function optionalString(value, max = 5000) {
  if (value == null) return '';
  if (typeof value !== 'string' || value.length > max) {
    throw new HttpError(400, 'Un campo de texto tiene un formato o longitud inválidos.', 'VALIDATION_ERROR');
  }
  return value.trim();
}

export function normalizeEmail(value) {
  const email = requiredString(value, 'email', 254).toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    throw new HttpError(400, 'Ingresa un correo electrónico válido.', 'VALIDATION_ERROR');
  }
  return email;
}

export function validatePassword(value) {
  if (typeof value !== 'string' || value.length < 10 || value.length > 128) {
    throw new HttpError(400, 'La contraseña debe tener entre 10 y 128 caracteres.', 'VALIDATION_ERROR');
  }
  return value;
}

export function validateCustomerPayload(body) {
  return {
    fullName: requiredString(body.fullName, 'nombre', 140),
    email: normalizeEmail(body.email),
    phone: body.phone == null || body.phone === '' ? null : requiredString(body.phone, 'teléfono', 30),
    password: validatePassword(body.password)
  };
}

export function validateBusinessPayload(body) {
  const modes = Array.isArray(body.modes) ? [...new Set(body.modes)] : [];
  if (!modes.length || modes.some((mode) => !['CITA', 'RECOJO'].includes(mode))) {
    throw new HttpError(400, 'Selecciona al menos una modalidad válida: CITA o RECOJO.', 'VALIDATION_ERROR');
  }
  const latitude = body.latitude == null || body.latitude === '' ? null : Number(body.latitude);
  const longitude = body.longitude == null || body.longitude === '' ? null : Number(body.longitude);
  if ((latitude == null) !== (longitude == null) || (latitude != null && (!Number.isFinite(latitude) || latitude < -90 || latitude > 90)) || (longitude != null && (!Number.isFinite(longitude) || longitude < -180 || longitude > 180))) {
    throw new HttpError(400, 'Las coordenadas de ubicación no son válidas.', 'VALIDATION_ERROR');
  }
  return {
    name: requiredString(body.name, 'nombre del negocio', 140),
    categoryId: requiredString(body.categoryId, 'categoría', 36),
    description: optionalString(body.description, 4000),
    phone: requiredString(body.phone, 'teléfono del negocio', 30),
    contactEmail: body.contactEmail ? normalizeEmail(body.contactEmail) : null,
    department: requiredString(body.department, 'departamento', 100),
    province: requiredString(body.province, 'provincia', 100),
    district: requiredString(body.district, 'distrito', 100),
    address: requiredString(body.address, 'dirección', 220),
    reference: optionalString(body.reference, 500),
    latitude,
    longitude,
    modes
  };
}

export function validateListingPayload(body) {
  const kind = body.kind;
  const mode = body.mode;
  const price = Number(body.price);
  const compareAtPrice = body.compareAtPrice == null || body.compareAtPrice === '' ? null : Number(body.compareAtPrice);
  const durationMinutes = body.durationMinutes == null || body.durationMinutes === '' ? null : Number(body.durationMinutes);
  const stock = body.stock == null || body.stock === '' ? null : Number(body.stock);
  if (!['SERVICIO', 'PRODUCTO'].includes(kind)) throw new HttpError(400, 'El tipo debe ser SERVICIO o PRODUCTO.', 'VALIDATION_ERROR');
  if (!['CITA', 'RECOJO', 'AMBOS'].includes(mode)) throw new HttpError(400, 'La modalidad debe ser CITA, RECOJO o AMBOS.', 'VALIDATION_ERROR');
  if (!Number.isFinite(price) || price < 0) throw new HttpError(400, 'El precio no es válido.', 'VALIDATION_ERROR');
  if (compareAtPrice != null && (!Number.isFinite(compareAtPrice) || compareAtPrice < price)) {
    throw new HttpError(400, 'El precio anterior debe ser igual o mayor al precio actual.', 'VALIDATION_ERROR');
  }
  if (durationMinutes != null && (!Number.isInteger(durationMinutes) || durationMinutes < 5 || durationMinutes > 1440)) {
    throw new HttpError(400, 'La duración debe estar entre 5 y 1440 minutos.', 'VALIDATION_ERROR');
  }
  if (stock != null && (!Number.isInteger(stock) || stock < 0)) throw new HttpError(400, 'El stock no puede ser negativo.', 'VALIDATION_ERROR');
  if (kind === 'PRODUCTO' && mode !== 'RECOJO') throw new HttpError(400, 'Los productos se reservan únicamente para recoger.', 'VALIDATION_ERROR');
  if (kind === 'PRODUCTO' && durationMinutes != null) throw new HttpError(400, 'Los productos no llevan duración de cita.', 'VALIDATION_ERROR');
  if (kind === 'SERVICIO' && mode === 'RECOJO') throw new HttpError(400, 'Los servicios deben ofrecer CITA o AMBOS.', 'VALIDATION_ERROR');
  return {
    title: requiredString(body.title, 'título', 140),
    description: optionalString(body.description, 4000),
    kind,
    mode,
    price,
    compareAtPrice,
    durationMinutes: kind === 'SERVICIO' ? (durationMinutes ?? 30) : null,
    stock
  };
}

export function assertBusinessHoursConfigured(activeHoursCount) {
  const count = Number(activeHoursCount);
  if (!Number.isInteger(count) || count <= 0) {
    throw new HttpError(
      409,
      'El negocio todavía no configuró horarios de atención. No se pueden aceptar reservas hasta definirlos.',
      'BUSINESS_HOURS_NOT_CONFIGURED'
    );
  }
  return count;
}

export function validateIdempotencyKey(value) {
  const key = requiredString(value, 'Idempotency-Key', 128);
  if (key.length < 8 || !/^[A-Za-z0-9._:-]+$/.test(key)) {
    throw new HttpError(400, 'La clave de idempotencia debe tener entre 8 y 128 caracteres seguros.', 'INVALID_IDEMPOTENCY_KEY');
  }
  return key;
}

export function validateAvailabilityDate(value) {
  const date = requiredString(value, 'fecha', 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) {
    throw new HttpError(400, 'La fecha debe tener el formato AAAA-MM-DD.', 'INVALID_DATE');
  }
  const parsed = new Date(`${date}T00:00:00.000Z`);
  if (!Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== date) {
    throw new HttpError(400, 'La fecha indicada no existe.', 'INVALID_DATE');
  }
  return date;
}

export function validateReservationPayload(body, { allowPast = false } = {}) {
  const mode = body.mode;
  const quantity = body.quantity == null ? 1 : Number(body.quantity);
  const paymentMethod = body.paymentMethod;
  const requestedAt = body.dateTime;
  if (!['CITA', 'RECOJO'].includes(mode)) throw new HttpError(400, 'La modalidad debe ser CITA o RECOJO.', 'VALIDATION_ERROR');
  if (!['EN_LINEA', 'EN_LOCAL'].includes(paymentMethod)) throw new HttpError(400, 'Selecciona un medio de pago válido.', 'VALIDATION_ERROR');
  if (!Number.isInteger(quantity) || quantity < 1 || quantity > 99) throw new HttpError(400, 'La cantidad debe estar entre 1 y 99.', 'VALIDATION_ERROR');
  if (typeof requestedAt !== 'string' || !/(Z|[+-]\d{2}:\d{2})$/i.test(requestedAt)) {
    throw new HttpError(400, 'La fecha debe incluir zona horaria, por ejemplo -05:00.', 'VALIDATION_ERROR');
  }
  const date = new Date(requestedAt);
  if (!Number.isFinite(date.getTime())) throw new HttpError(400, 'La fecha y hora no son válidas.', 'VALIDATION_ERROR');
  if (!allowPast) assertReservationDateFuture(date);
  const staffId = body.staffId == null || body.staffId === '' ? null : requiredString(body.staffId, 'trabajador', 36);
  const resourceId = body.resourceId == null || body.resourceId === '' ? null : requiredString(body.resourceId, 'recurso', 36);
  if (staffId && resourceId) {
    throw new HttpError(400, 'Selecciona un trabajador o un recurso, no ambos.', 'INVALID_ASSIGNEE');
  }
  if (mode === 'RECOJO' && (staffId || resourceId)) {
    throw new HttpError(400, 'La recogida no admite asignación de personal o recursos de citas.', 'INVALID_RESERVATION_MODE');
  }
  return {
    mode,
    quantity,
    paymentMethod,
    date,
    note: optionalString(body.note, 1000),
    staffId,
    resourceId
  };
}

export function assertReservationDateFuture(date) {
  if (!(date instanceof Date) || !Number.isFinite(date.getTime()) || date.getTime() <= Date.now()) {
    throw new HttpError(400, 'La fecha y hora deben ser futuras.', 'VALIDATION_ERROR');
  }
  return date;
}

const transitions = {
  SOLICITADA: new Set(['CONFIRMADA', 'CANCELADA']),
  CONFIRMADA: new Set(['LISTA', 'COMPLETADA', 'CANCELADA', 'NO_SHOW']),
  LISTA: new Set(['COMPLETADA', 'CANCELADA']),
  COMPLETADA: new Set(),
  CANCELADA: new Set(),
  NO_SHOW: new Set()
};

export function canTransitionReservation(current, next) {
  return Boolean(transitions[current]?.has(next));
}

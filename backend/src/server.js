import { createServer } from 'node:http';
import { createHash, randomBytes, randomUUID } from 'node:crypto';
import { createPool, withTransaction } from './db.js';
import {
  HttpError, assertBusinessHoursConfigured, assertReservationDateFuture, canTransitionReservation, normalizeEmail, requiredString,
  validateAvailabilityDate, validateIdempotencyKey,
  validateBusinessPayload, validateCustomerPayload, validateListingPayload,
  validateReservationPayload
} from './domain.js';
import { bearerToken, createAccessToken, hashPassword, verifyPassword } from './security.js';
import { decodeBusinessCursor, encodeBusinessCursor, parsePageSize } from './pagination.js';

const PORT = Number(process.env.PORT || 8080);
const DATABASE_URL = process.env.DATABASE_URL;
const JWT_SECRET = process.env.JWT_SECRET || '';
const origins = new Set((process.env.ALLOWED_ORIGINS || '').split(',').map((v) => v.trim()).filter(Boolean));
const MAX_BODY_BYTES = 1024 * 1024;
const TRUST_PROXY = process.env.TRUST_PROXY === 'true';
const pool = createPool(DATABASE_URL);
const rateBuckets = new Map();

if (!DATABASE_URL) throw new Error('DATABASE_URL es obligatorio.');
if (Buffer.byteLength(JWT_SECRET) < 32) throw new Error('JWT_SECRET debe contener al menos 32 bytes aleatorios.');

function json(response, status, payload) {
  const body = JSON.stringify(payload);
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': 'no-referrer',
    'X-Frame-Options': 'DENY'
  });
  response.end(body);
}

function rateLimit(request, key, limit = 30, windowMs = 60_000) {
  const forwarded = TRUST_PROXY ? request.headers['x-forwarded-for']?.toString().split(',')[0].trim() : null;
  const ip = forwarded || request.socket.remoteAddress || 'unknown';
  const bucketKey = `${key}:${ip}`;
  const now = Date.now();
  const bucket = rateBuckets.get(bucketKey);
  if (rateBuckets.size > 5000) {
    for (const [key, value] of rateBuckets) if (value.resetAt <= now) rateBuckets.delete(key);
  }
  if (!bucket || bucket.resetAt <= now) {
    rateBuckets.set(bucketKey, { count: 1, resetAt: now + windowMs });
    return;
  }
  bucket.count += 1;
  if (bucket.count > limit) throw new HttpError(429, 'Demasiadas solicitudes. Inténtalo nuevamente más tarde.', 'RATE_LIMITED');
}

async function readJson(request) {
  let size = 0;
  const chunks = [];
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) throw new HttpError(413, 'El cuerpo de la solicitud supera el límite permitido.', 'PAYLOAD_TOO_LARGE');
    chunks.push(chunk);
  }
  if (!size) return {};
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    throw new HttpError(400, 'El cuerpo JSON no es válido.', 'INVALID_JSON');
  }
}

function authUser(request) {
  return bearerToken(request, JWT_SECRET);
}

function mapPgError(error) {
  if (error instanceof HttpError) return error;
  if (error?.code === '23505') return new HttpError(409, 'Ya existe un registro con esos datos.', 'DUPLICATE');
  if (error?.code === '23P01') return new HttpError(409, 'Ese horario acaba de ser reservado. Elige otro horario.', 'SLOT_UNAVAILABLE');
  if (error?.code === 'P0001' && error?.message === 'STOCK_UNAVAILABLE') return new HttpError(409, 'No hay stock suficiente. Actualiza el catálogo.', 'STOCK_UNAVAILABLE');
  if (error?.code === '23503' || error?.code === '22P02' || error?.code === '23514') return new HttpError(400, 'Los datos enviados no son válidos o hacen referencia a un registro inexistente.', 'INVALID_REFERENCE');
  if (error?.code === '42501') return new HttpError(403, 'No tienes permiso para realizar esta operación.', 'FORBIDDEN');
  return new HttpError(500, 'Ocurrió un error interno. Inténtalo nuevamente.', 'INTERNAL_ERROR');
}

async function ensureMembership(client, businessId, userId, roles = ['OWNER', 'MANAGER']) {
  const result = await client.query(
    `SELECT role FROM business_members WHERE business_id = $1 AND user_id = $2 AND status = 'ACTIVE' AND role = ANY($3::text[])`,
    [businessId, userId, roles]
  );
  if (!result.rowCount) throw new HttpError(403, 'No tienes permisos para administrar este negocio.', 'BUSINESS_FORBIDDEN');
  return result.rows[0].role;
}

async function ensureBusinessSubscription(client, businessId) {
  const result = await client.query(
    `SELECT status FROM subscriptions WHERE business_id = $1
      AND status IN ('TRIAL','ACTIVE') AND (ends_at IS NULL OR ends_at >= now())
      ORDER BY ends_at DESC NULLS FIRST LIMIT 1`, [businessId]
  );
  if (!result.rowCount) throw new HttpError(402, 'La suscripción del negocio venció. Renueva el plan para publicar y recibir nuevas reservas.', 'SUBSCRIPTION_REQUIRED');
}

async function createBusinessRecord(client, ownerId, payload) {
  const category = await client.query('SELECT id FROM categories WHERE id = $1 AND active = true', [payload.categoryId]);
  if (!category.rowCount) throw new HttpError(400, 'La categoría seleccionada no existe o está inactiva.', 'INVALID_CATEGORY');

  const inserted = await client.query(
    `INSERT INTO businesses(owner_user_id, category_id, name, description, phone, contact_email,
       department, province, district, address, reference, latitude, longitude, time_zone)
     VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,'America/Lima')
     RETURNING id, name, created_at`,
    [ownerId, payload.categoryId, payload.name, payload.description, payload.phone, payload.contactEmail,
      payload.department, payload.province, payload.district, payload.address, payload.reference,
      payload.latitude, payload.longitude]
  );
  const business = inserted.rows[0];
  await client.query(
    `INSERT INTO business_members(business_id,user_id,role,status) VALUES($1,$2,'OWNER','ACTIVE')`,
    [business.id, ownerId]
  );
  await client.query(
    `INSERT INTO business_resources(business_id,name,kind,is_default,status) VALUES($1,'Atención general 1','CAPACITY',true,'ACTIVE')`,
    [business.id]
  );
  const plan = await client.query("SELECT id, trial_days FROM plans WHERE code = 'TRIAL' AND active = true");
  if (!plan.rowCount) throw new HttpError(500, 'No se encontró el plan de prueba.', 'TRIAL_PLAN_MISSING');
  await client.query(
    `INSERT INTO subscriptions(business_id,plan_id,status,started_at,ends_at,auto_renew)
     VALUES($1,$2,'TRIAL',now(),now() + ($3::text || ' days')::interval,false)`,
    [business.id, plan.rows[0].id, plan.rows[0].trial_days]
  );
  for (const mode of payload.modes) {
    await client.query('INSERT INTO business_modes(business_id,mode) VALUES($1,$2)', [business.id, mode]);
  }
  return business;
}

async function registerCustomer(body) {
  const payload = validateCustomerPayload(body);
  const userId = randomUUID();
  const passwordHash = await hashPassword(payload.password);
  const user = await withTransaction(pool, userId, async (client) => {
    const result = await client.query(
      `INSERT INTO users(id,full_name,email,phone,password_hash)
       VALUES($1,$2,$3,$4,$5) RETURNING id,full_name,email,phone`,
      [userId, payload.fullName, payload.email, payload.phone, passwordHash]
    );
    return result.rows[0];
  });
  return { user, token: createAccessToken(user, JWT_SECRET) };
}

async function registerBusiness(body) {
  const owner = {
    fullName: requiredString(body.fullName, 'nombre del responsable', 140),
    email: validateCustomerEmail(body.email),
    phone: body.ownerPhone ? requiredString(body.ownerPhone, 'teléfono del responsable', 30) : null,
    password: typeof body.password === 'string' ? body.password : ''
  };
  if (owner.password.length < 10 || owner.password.length > 128) throw new HttpError(400, 'La contraseña debe tener entre 10 y 128 caracteres.', 'VALIDATION_ERROR');
  const businessPayload = validateBusinessPayload(body.business || body);
  const userId = randomUUID();
  const passwordHash = await hashPassword(owner.password);
  const result = await withTransaction(pool, userId, async (client) => {
    const userResult = await client.query(
      `INSERT INTO users(id,full_name,email,phone,password_hash)
       VALUES($1,$2,$3,$4,$5) RETURNING id,full_name,email,phone`,
      [userId, owner.fullName, owner.email, owner.phone || businessPayload.phone, passwordHash]
    );
    const business = await createBusinessRecord(client, userId, businessPayload);
    return { user: userResult.rows[0], business };
  });
  return { ...result, token: createAccessToken(result.user, JWT_SECRET) };
}

function validateCustomerEmail(email) {
  return normalizeEmail(email);
}

function asBusiness(row) {
  return {
    id: row.id,
    name: row.name,
    description: row.description,
    category: row.category_name,
    emoji: row.emoji,
    phone: row.phone,
    department: row.department,
    province: row.province,
    district: row.district,
    address: row.address,
    modes: row.modes || [],
    subscriptionStatus: row.subscription_status,
    trialEndsAt: row.trial_ends_at
  };
}

function reservationFingerprint({ businessId, listingId, payload }) {
  const canonical = JSON.stringify({
    businessId,
    listingId,
    mode: payload.mode,
    dateTime: payload.date.toISOString(),
    quantity: payload.quantity,
    paymentMethod: payload.paymentMethod,
    note: payload.note,
    staffId: payload.staffId,
    resourceId: payload.resourceId
  });
  return createHash('sha256').update(canonical).digest('hex');
}

function assertSameIdempotentRequest(existing, fingerprint) {
  if (existing.request_fingerprint !== fingerprint) {
    throw new HttpError(409, 'La clave de idempotencia ya fue utilizada con datos diferentes. Inicia una nueva solicitud.', 'IDEMPOTENCY_KEY_REUSED');
  }
  return { ...existing, replayed: true };
}

async function assignmentIsFree(client, staffId, resourceId, startsAt, endsAt) {
  const result = await client.query(
    `SELECT
       ($1::uuid IS NULL OR appointment_assignee_available('STAFF',$1,$3::timestamptz,$4::timestamptz))
       AND ($2::uuid IS NULL OR appointment_assignee_available('RESOURCE',$2,$3::timestamptz,$4::timestamptz))
       AS available`,
    [staffId, resourceId, startsAt, endsAt]
  );
  return result.rows[0].available;
}

async function lockAssignee(client, type, id, wait = true) {
  const lockKey = `${type}:${id}`;
  const result = await client.query(
    wait
      ? 'SELECT pg_advisory_xact_lock(hashtextextended($1,0)) AS locked'
      : 'SELECT pg_try_advisory_xact_lock(hashtextextended($1,0)) AS locked',
    [lockKey]
  );
  return wait || result.rows[0].locked;
}

async function findAndLockAssignee(client, businessId, startsAt, endsAt, requestedStaffId, requestedResourceId) {
  let staffId = requestedStaffId;
  let resourceId = requestedResourceId;

  if (staffId) {
    const staff = await client.query(
      `SELECT id FROM business_staff WHERE business_id=$1 AND id=$2 AND status='ACTIVE'`, [businessId, staffId]
    );
    if (!staff.rowCount) throw new HttpError(409, 'El trabajador seleccionado ya no está disponible.', 'ASSIGNEE_UNAVAILABLE');
  }
  if (resourceId) {
    const resource = await client.query(
      `SELECT id FROM business_resources WHERE business_id=$1 AND id=$2 AND status='ACTIVE'`, [businessId, resourceId]
    );
    if (!resource.rowCount) throw new HttpError(409, 'El recurso seleccionado ya no está disponible.', 'ASSIGNEE_UNAVAILABLE');
  }

  const staffCount = await client.query(
    `SELECT count(*)::int AS count FROM business_staff WHERE business_id=$1 AND status='ACTIVE'`, [businessId]
  );
  if (resourceId && !staffId && staffCount.rows[0].count > 0) {
    throw new HttpError(400, 'Este negocio asigna las citas a trabajadores. Selecciona un trabajador disponible.', 'STAFF_REQUIRED');
  }

  if (staffId || resourceId) {
    if (staffId) await lockAssignee(client, 'staff', staffId, true);
    if (resourceId) await lockAssignee(client, 'resource', resourceId, true);
    if (!(await assignmentIsFree(client, staffId, resourceId, startsAt, endsAt))) {
      throw new HttpError(409, 'El trabajador o recurso seleccionado acaba de ocuparse. Actualiza los horarios.', 'SLOT_UNAVAILABLE');
    }
    return { staffId, resourceId };
  }

  const useStaff = staffCount.rows[0].count > 0;
  const candidates = useStaff
    ? await client.query(
      `SELECT id FROM business_staff WHERE business_id=$1 AND status='ACTIVE' ORDER BY name,id LIMIT 200`, [businessId]
    )
    : await client.query(
      `SELECT id FROM business_resources WHERE business_id=$1 AND status='ACTIVE' ORDER BY name,id LIMIT 200`, [businessId]
    );

  for (const candidate of candidates.rows) {
    const id = candidate.id;
    const acquired = await lockAssignee(client, useStaff ? 'staff' : 'resource', id, false);
    if (!acquired) continue;
    const nextStaffId = useStaff ? id : null;
    const nextResourceId = useStaff ? null : id;
    if (await assignmentIsFree(client, nextStaffId, nextResourceId, startsAt, endsAt)) {
      return { staffId: nextStaffId, resourceId: nextResourceId };
    }
  }
  throw new HttpError(409, 'No hay trabajadores o recursos disponibles en ese horario. Elige otro turno.', 'SLOT_UNAVAILABLE');
}

async function handle(request, response) {
  const origin = request.headers.origin;
  if (origin && origins.size && !origins.has(origin)) throw new HttpError(403, 'Origen no permitido.', 'CORS_REJECTED');
  if (origin && origins.has(origin)) {
    response.setHeader('Access-Control-Allow-Origin', origin);
    response.setHeader('Vary', 'Origin');
  }
  response.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type, Idempotency-Key');
  response.setHeader('Access-Control-Allow-Methods', 'GET, POST, PATCH, DELETE, OPTIONS');
  if (request.method === 'OPTIONS') {
    response.writeHead(204);
    response.end();
    return;
  }

  const url = new URL(request.url, `http://${request.headers.host || 'localhost'}`);
  const path = url.pathname.replace(/\/$/, '') || '/';
  const method = request.method;

  if (method === 'GET' && path === '/health') {
    await pool.query('SELECT 1');
    json(response, 200, { status: 'ok', service: 'unamad-citas-marketplace-api', database: 'connected' });
    return;
  }

  if (method === 'GET' && path === '/api/v1/categories') {
    const result = await withTransaction(pool, null, (client) => client.query(
      'SELECT id,name,slug,emoji FROM categories WHERE active = true ORDER BY name'
    ));
    json(response, 200, { data: result.rows });
    return;
  }

  if (method === 'POST' && path === '/api/v1/auth/register/customer') {
    rateLimit(request, 'register', 8);
    const result = await registerCustomer(await readJson(request));
    json(response, 201, { data: result });
    return;
  }

  if (method === 'POST' && path === '/api/v1/auth/register/business') {
    rateLimit(request, 'register', 8);
    const result = await registerBusiness(await readJson(request));
    json(response, 201, { data: result });
    return;
  }

  if (method === 'POST' && path === '/api/v1/auth/login') {
    rateLimit(request, 'login', 12);
    const body = await readJson(request);
    const email = validateCustomerEmail(body.email);
    if (typeof body.password !== 'string' || !body.password || body.password.length < 10 || body.password.length > 128) throw new HttpError(400, 'La contraseña debe tener entre 10 y 128 caracteres.', 'VALIDATION_ERROR');
    const result = await pool.query(
      `SELECT id,full_name,email,phone,password_hash,status FROM auth_lookup_user($1)`, [email]
    );
    if (!result.rowCount || result.rows[0].status !== 'ACTIVE' || !(await verifyPassword(body.password, result.rows[0].password_hash))) {
      throw new HttpError(401, 'Correo o contraseña incorrectos.', 'INVALID_CREDENTIALS');
    }
    const user = result.rows[0];
    await withTransaction(pool, user.id, (client) => client.query('UPDATE users SET last_login_at = now() WHERE id = $1', [user.id]));
    delete user.password_hash;
    delete user.status;
    json(response, 200, { data: { user, token: createAccessToken(user, JWT_SECRET) } });
    return;
  }

  if (method === 'GET' && path === '/api/v1/businesses') {
    const term = (url.searchParams.get('q') || '').trim();
    const categoryId = url.searchParams.get('categoryId');
    const district = (url.searchParams.get('district') || '').trim();
    const pageSize = parsePageSize(url.searchParams.get('limit'));
    const cursor = decodeBusinessCursor(url.searchParams.get('cursor'));
    const result = await withTransaction(pool, null, (client) => client.query(
      `SELECT b.id,b.name,b.description,b.phone,b.department,b.province,b.district,b.address,b.created_at::text AS cursor_created_at,
        c.name AS category_name,c.emoji,
        COALESCE((SELECT array_agg(bm.mode ORDER BY bm.mode) FROM business_modes bm WHERE bm.business_id=b.id),'{}') AS modes,
        (SELECT s.status FROM subscriptions s WHERE s.business_id=b.id ORDER BY s.created_at DESC LIMIT 1) AS subscription_status,
        (SELECT s.ends_at FROM subscriptions s WHERE s.business_id=b.id ORDER BY s.created_at DESC LIMIT 1) AS trial_ends_at
       FROM businesses b JOIN categories c ON c.id=b.category_id
       WHERE b.state='ACTIVE' AND b.visible_marketplace=true AND is_public_business(b.id)
         AND ($1='' OR b.name ILIKE '%'||$1||'%' OR b.description ILIKE '%'||$1||'%' OR c.name ILIKE '%'||$1||'%'
              OR EXISTS (SELECT 1 FROM listings l WHERE l.business_id=b.id AND l.active=true
                         AND (l.title ILIKE '%'||$1||'%' OR l.description ILIKE '%'||$1||'%')))
         AND ($2::uuid IS NULL OR b.category_id=$2::uuid)
         AND ($3='' OR b.district ILIKE '%'||$3||'%')
         AND ($4::timestamptz IS NULL OR (b.created_at,b.id) < ($4::timestamptz,$5::uuid))
       ORDER BY b.created_at DESC,b.id DESC LIMIT $6`,
      [term, categoryId || null, district, cursor?.createdAt ?? null, cursor?.id ?? null, pageSize + 1]
    ));
    const hasMore = result.rows.length > pageSize;
    const rows = hasMore ? result.rows.slice(0, pageSize) : result.rows;
    const nextCursor = hasMore && rows.length ? encodeBusinessCursor(rows[rows.length - 1]) : null;
    json(response, 200, {
      data: rows.map(asBusiness),
      page: { limit: pageSize, hasMore, nextCursor }
    });
    return;
  }

  if (method === 'GET' && path === '/api/v1/me') {
    const user = authUser(request);
    const result = await withTransaction(pool, user.id, (client) => client.query(
      'SELECT id,full_name,email,phone,created_at FROM users WHERE id=$1', [user.id]
    ));
    if (!result.rowCount) throw new HttpError(404, 'No se encontró la cuenta.', 'USER_NOT_FOUND');
    json(response, 200, { data: result.rows[0] });
    return;
  }

  if (method === 'GET' && path === '/api/v1/me/businesses') {
    const user = authUser(request);
    const result = await withTransaction(pool, user.id, (client) => client.query(
      `SELECT b.id,b.name,b.state,b.district,b.created_at,bm.role,
        (SELECT s.status FROM subscriptions s WHERE s.business_id=b.id ORDER BY s.created_at DESC LIMIT 1) AS subscription_status,
        (SELECT s.ends_at FROM subscriptions s WHERE s.business_id=b.id ORDER BY s.created_at DESC LIMIT 1) AS subscription_ends_at
       FROM business_members bm JOIN businesses b ON b.id=bm.business_id
       WHERE bm.user_id=$1 AND bm.status='ACTIVE' ORDER BY b.created_at DESC`, [user.id]
    ));
    json(response, 200, { data: result.rows });
    return;
  }

  if (method === 'GET' && path === '/api/v1/me/reservations') {
    const user = authUser(request);
    const result = await withTransaction(pool, user.id, (client) => client.query(
      `SELECT r.id,r.code,r.mode,r.status,r.payment_method,r.payment_state,r.quantity,r.total,
        COALESCE(r.starts_at,r.pickup_at) AS scheduled_at,b.id AS business_id,b.name AS business_name,l.title AS listing_title,
        COALESCE(s.name,br.name,'Sin asignación') AS assignee_name,
        CASE WHEN r.assigned_staff_id IS NOT NULL THEN 'TRABAJADOR' WHEN r.assigned_resource_id IS NOT NULL THEN 'RECURSO' ELSE NULL END AS assignee_type
       FROM reservations r JOIN businesses b ON b.id=r.business_id JOIN listings l ON l.id=r.listing_id
       LEFT JOIN business_staff s ON s.id=r.assigned_staff_id
       LEFT JOIN business_resources br ON br.id=r.assigned_resource_id
       WHERE r.customer_user_id=$1 ORDER BY r.created_at DESC LIMIT 100`, [user.id]
    ));
    json(response, 200, { data: result.rows });
    return;
  }

  if (method === 'POST' && path === '/api/v1/me/reservations') {
    const user = authUser(request);
    const body = await readJson(request);
    const businessId = requiredString(body.businessId, 'negocio', 36);
    const listingId = requiredString(body.listingId, 'publicación', 36);
    const idempotencyKey = validateIdempotencyKey(request.headers['idempotency-key'] || body.idempotencyKey);
    // Permite leer una repetición histórica ya registrada: la idempotencia se
    // comprueba antes de exigir que el horario siga siendo futuro.
    const payload = validateReservationPayload(body, { allowPast: true });
    const fingerprint = reservationFingerprint({ businessId, listingId, payload });

    const result = await withTransaction(pool, user.id, async (client) => {
      // Serializa también las peticiones idénticas que llegan al mismo tiempo.
      // Así la segunda espera a la primera y luego recupera la reserva ya confirmada,
      // en vez de fallar como si el horario estuviera ocupado.
      await lockAssignee(client, 'idempotency', `${user.id}:${idempotencyKey}`, true);

      // Repetir la misma petición devuelve la reserva original, sin volver a descontar stock ni crear pagos.
      const previous = await client.query(
        `SELECT id,code,status,payment_state,total,request_fingerprint,assigned_staff_id,assigned_resource_id
         FROM reservations WHERE customer_user_id=$1 AND idempotency_key=$2`,
        [user.id, idempotencyKey]
      );
      if (previous.rowCount) return assertSameIdempotentRequest(previous.rows[0], fingerprint);

      assertReservationDateFuture(payload.date);

      const listingResult = await client.query(
        `SELECT l.*,b.time_zone FROM listings l JOIN businesses b ON b.id=l.business_id
         WHERE l.id=$1 AND l.business_id=$2 AND l.active=true`, [listingId, businessId]
      );
      if (!listingResult.rowCount) throw new HttpError(404, 'La publicación no está disponible.', 'LISTING_NOT_FOUND');
      const listing = listingResult.rows[0];
      const modesResult = await client.query('SELECT mode FROM business_modes WHERE business_id=$1', [businessId]);
      const businessModes = new Set(modesResult.rows.map((row) => row.mode));
      if (!businessModes.has(payload.mode)) throw new HttpError(400, 'El negocio no ofrece esa modalidad.', 'MODE_NOT_AVAILABLE');
      if (listing.mode !== 'AMBOS' && listing.mode !== payload.mode) throw new HttpError(400, 'La publicación no admite esa modalidad.', 'MODE_NOT_AVAILABLE');
      if (payload.mode === 'CITA' && listing.kind !== 'SERVICIO') throw new HttpError(400, 'Solo los servicios aceptan citas.', 'INVALID_RESERVATION_MODE');
      if (payload.mode === 'RECOJO' && listing.kind !== 'PRODUCTO') throw new HttpError(400, 'La recogida requiere un producto.', 'INVALID_RESERVATION_MODE');
      if (payload.mode === 'RECOJO' && (payload.staffId || payload.resourceId)) {
        throw new HttpError(400, 'La asignación de trabajador o recurso solo aplica a citas.', 'INVALID_RESERVATION_MODE');
      }
      if (listing.stock != null && listing.stock < payload.quantity) {
        throw new HttpError(409, 'No hay stock suficiente. Actualiza el catálogo.', 'STOCK_UNAVAILABLE');
      }

      let startsAt = null;
      let endsAt = null;
      let pickupAt = null;
      let assignedStaffId = null;
      let assignedResourceId = null;
      if (payload.mode === 'CITA') {
        startsAt = payload.date;
        const duration = Math.max(5, Number(listing.duration_minutes || 30));
        endsAt = new Date(startsAt.getTime() + duration * 60_000);
        const assignment = await findAndLockAssignee(
          client, businessId, startsAt, endsAt, payload.staffId, payload.resourceId
        );
        assignedStaffId = assignment.staffId;
        assignedResourceId = assignment.resourceId;
      } else {
        pickupAt = payload.date;
      }

      // Horarios siempre se interpretan en la zona horaria configurada para el negocio.
      const local = await client.query(
        `SELECT EXTRACT(ISODOW FROM ($2::timestamptz AT TIME ZONE b.time_zone))::int AS day,
           EXTRACT(ISODOW FROM ($3::timestamptz AT TIME ZONE b.time_zone))::int AS end_day,
           ($2::timestamptz AT TIME ZONE b.time_zone)::time AS start_local,
           ($3::timestamptz AT TIME ZONE b.time_zone)::time AS end_local
         FROM businesses b WHERE b.id=$1`, [businessId, startsAt || pickupAt, endsAt || pickupAt]
      );
      const { day, end_day: endDay, start_local: startLocal, end_local: endLocal } = local.rows[0];
      const activeHours = await client.query(
        'SELECT count(*)::int AS count FROM business_hours WHERE business_id=$1 AND active=true', [businessId]
      );
      assertBusinessHoursConfigured(activeHours.rows[0]?.count ?? 0);
      if (day !== endDay) {
        throw new HttpError(400, 'La reserva no puede cruzar al siguiente día de atención.', 'OUTSIDE_BUSINESS_HOURS');
      }
      const open = await client.query(
        `SELECT 1 FROM business_hours WHERE business_id=$1 AND day_of_week=$2 AND active=true
          AND opens_at <= $3::time AND closes_at >= $4::time LIMIT 1`,
        [businessId, day, startLocal, endLocal]
      );
      if (!open.rowCount) {
        throw new HttpError(400, 'El horario solicitado está fuera del horario de atención.', 'OUTSIDE_BUSINESS_HOURS');
      }

      const code = `RSV-${randomBytes(4).toString('hex').toUpperCase()}`;
      const total = (Number(listing.price) * payload.quantity).toFixed(2);
      // El advisory lock + la consulta previa resuelven los reintentos; se deja
      // actuar al índice UNIQUE como defensa final. No usar ON CONFLICT DO NOTHING:
      // RECOJO descuenta stock en un trigger BEFORE INSERT.
      const inserted = await client.query(
        `INSERT INTO reservations(code,business_id,listing_id,customer_user_id,mode,starts_at,ends_at,pickup_at,
          assigned_staff_id,assigned_resource_id,idempotency_key,request_fingerprint,
          quantity,unit_price,total,payment_method,note)
         VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17)
         RETURNING id,code,status,payment_state,total,assigned_staff_id,assigned_resource_id,request_fingerprint`,
        [code,businessId,listingId,user.id,payload.mode,startsAt,endsAt,pickupAt,
          assignedStaffId,assignedResourceId,idempotencyKey,fingerprint,
          payload.quantity,listing.price,total,payload.paymentMethod,payload.note]
      );
      const reservation = inserted.rows[0];
      await client.query(
        `INSERT INTO payments(customer_user_id,reservation_id,concept,amount,currency,channel,state)
         VALUES($1,$2,'RESERVA',$3,'PEN',$4,'PENDIENTE')`,
        [user.id,reservation.id,total,payload.paymentMethod]
      );
      return { ...reservation, replayed: false };
    });
    json(response, result.replayed ? 200 : 201, {
      data: result,
      message: result.replayed ? 'La solicitud ya estaba registrada; se devuelve la reserva existente.' : 'Solicitud registrada. El negocio debe confirmarla.'
    });
    return;
  }

  if (method === 'POST' && path === '/api/v1/me/favorites') {
    const user = authUser(request);
    const body = await readJson(request);
    const businessId = requiredString(body.businessId, 'negocio', 36);
    await withTransaction(pool, user.id, async (client) => {
      const exists = await client.query('SELECT id FROM businesses WHERE id=$1 AND is_public_business(id)', [businessId]);
      if (!exists.rowCount) throw new HttpError(404, 'No se encontró el negocio.', 'BUSINESS_NOT_FOUND');
      await client.query('INSERT INTO favorites(user_id,business_id) VALUES($1,$2) ON CONFLICT DO NOTHING', [user.id,businessId]);
    });
    json(response, 201, { message: 'Negocio guardado en favoritos.' });
    return;
  }

  if (method === 'GET' && path === '/api/v1/me/favorites') {
    const user = authUser(request);
    const result = await withTransaction(pool, user.id, (client) => client.query(
      `SELECT b.id,b.name,b.district,b.phone,c.name AS category_name,c.emoji
       FROM favorites f JOIN businesses b ON b.id=f.business_id JOIN categories c ON c.id=b.category_id
       WHERE f.user_id=$1 ORDER BY f.created_at DESC`, [user.id]
    ));
    json(response, 200, { data: result.rows });
    return;
  }

  const availabilityMatch = /^\/api\/v1\/businesses\/([^/]+)\/availability$/.exec(path);
  if (method === 'GET' && availabilityMatch) {
    const businessId = availabilityMatch[1];
    const listingId = requiredString(url.searchParams.get('listingId'), 'servicio', 36);
    const localDate = validateAvailabilityDate(url.searchParams.get('date'));
    const selectedStaff = url.searchParams.get('staffId') || null;
    const result = await withTransaction(pool, null, async (client) => {
      const businessDate = await client.query(
        `SELECT to_char((now() AT TIME ZONE time_zone)::date, 'YYYY-MM-DD') AS today
         FROM businesses WHERE id=$1 AND is_public_business(id)`, [businessId]
      );
      if (!businessDate.rowCount) throw new HttpError(404, 'Negocio no encontrado o no disponible.', 'BUSINESS_NOT_FOUND');
      if (localDate < businessDate.rows[0].today) {
        throw new HttpError(400, 'No se puede consultar disponibilidad de una fecha pasada.', 'PAST_AVAILABILITY_DATE');
      }
      const hours = await client.query(
        `SELECT count(*)::int AS count FROM business_hours WHERE business_id=$1 AND active=true`, [businessId]
      );
      assertBusinessHoursConfigured(hours.rows[0]?.count ?? 0);
      const slots = await client.query(
        `WITH business AS (
           SELECT id,time_zone FROM businesses WHERE id=$1 AND is_public_business(id)
         ), listing AS (
           SELECT id,business_id,duration_minutes FROM listings
           WHERE id=$2 AND business_id=$1 AND active=true AND kind='SERVICIO'
             AND mode IN ('CITA','AMBOS')
             AND EXISTS (SELECT 1 FROM business_modes bm WHERE bm.business_id=$1 AND bm.mode='CITA')
         ), staff_candidates AS (
           SELECT s.id,s.name,'STAFF'::text AS assignee_type
           FROM business_staff s
           WHERE s.business_id=$1 AND s.status='ACTIVE'
             AND ($4::uuid IS NULL OR s.id=$4)
         ), resource_candidates AS (
           SELECT r.id,r.name,'RESOURCE'::text AS assignee_type
           FROM business_resources r
           WHERE r.business_id=$1 AND r.status='ACTIVE'
             AND NOT EXISTS (SELECT 1 FROM business_staff s WHERE s.business_id=$1 AND s.status='ACTIVE')
         ), candidates AS (
           SELECT * FROM staff_candidates UNION ALL SELECT * FROM resource_candidates
         ), working_hours AS (
           SELECT ( $3::date + h.opens_at ) AT TIME ZONE b.time_zone AS opens_at,
                  ( $3::date + h.closes_at ) AT TIME ZONE b.time_zone AS closes_at,
                  GREATEST(5,COALESCE(l.duration_minutes,30))::int AS duration_minutes
           FROM business b JOIN listing l ON l.business_id=b.id
           JOIN business_hours h ON h.business_id=b.id AND h.active=true
             AND h.day_of_week=EXTRACT(ISODOW FROM $3::date)::int
         ), possible_slots AS (
           SELECT c.id AS assignee_id,c.name AS assignee_name,c.assignee_type,
                  slot.starts_at,
                  slot.starts_at + make_interval(mins => w.duration_minutes) AS ends_at
           FROM working_hours w CROSS JOIN candidates c
           CROSS JOIN LATERAL generate_series(
             w.opens_at,
             w.closes_at - make_interval(mins => w.duration_minutes),
             interval '15 minutes'
           ) AS slot(starts_at)
         )
         SELECT assignee_id,assignee_name,assignee_type,starts_at,ends_at
         FROM possible_slots p
         WHERE p.starts_at > now()
           AND appointment_assignee_available(p.assignee_type,p.assignee_id,p.starts_at,p.ends_at)
         ORDER BY starts_at,assignee_name LIMIT 500`,
        [businessId, listingId, localDate, selectedStaff]
      );
      return slots.rows;
    });
    json(response, 200, { data: result, date: localDate, intervalMinutes: 15 });
    return;
  }

  const staffResourceMatch = /^\/api\/v1\/businesses\/([^/]+)\/(staff|resources)(?:\/([^/]+))?$/.exec(path);
  if (staffResourceMatch) {
    const businessId = staffResourceMatch[1];
    const collection = staffResourceMatch[2];
    const recordId = staffResourceMatch[3] || null;
    const table = collection === 'staff' ? 'business_staff' : 'business_resources';

    if (method === 'GET' && !recordId) {
      const user = request.headers.authorization ? authUser(request) : null;
      const result = await withTransaction(pool, user?.id || null, (client) => client.query(
        collection === 'staff'
          ? `SELECT id,business_id,name,specialty,status FROM business_staff WHERE business_id=$1 AND status='ACTIVE' ORDER BY name`
          : `SELECT id,business_id,name,kind,status FROM business_resources WHERE business_id=$1 AND status='ACTIVE' ORDER BY name`,
        [businessId]
      ));
      json(response, 200, { data: result.rows });
      return;
    }

    if (method === 'POST' && !recordId) {
      const user = authUser(request);
      const body = await readJson(request);
      const name = requiredString(body.name, collection === 'staff' ? 'nombre del trabajador' : 'nombre del recurso', 120);
      const result = await withTransaction(pool, user.id, async (client) => {
        await ensureMembership(client, businessId, user.id);
        await ensureBusinessSubscription(client, businessId);
        if (collection === 'staff') {
          const specialty = body.specialty == null ? '' : requiredString(body.specialty, 'especialidad', 160);
          const before = await client.query(
            `SELECT count(*)::int AS count FROM business_staff WHERE business_id=$1 AND status='ACTIVE'`, [businessId]
          );
          const inserted = await client.query(
            `INSERT INTO business_staff(business_id,name,specialty) VALUES($1,$2,$3)
             RETURNING id,business_id,name,specialty,status`, [businessId,name,specialty]
          );
          if (before.rows[0].count === 0) {
            // Las citas previas sin trabajador quedan asociadas al primer trabajador creado.
            // La restricción antigua por negocio impedía que esas citas se solaparan.
            await client.query(
              `UPDATE reservations SET assigned_staff_id=$1,assigned_resource_id=NULL,updated_at=now()
               WHERE business_id=$2 AND mode='CITA' AND status NOT IN ('CANCELADA','NO_SHOW')
                 AND assigned_resource_id IN (
                   SELECT id FROM business_resources WHERE business_id=$2 AND is_default=true
                 )`, [inserted.rows[0].id,businessId]
            );
          }
          return inserted.rows[0];
        }
        const kind = body.kind || 'CAPACITY';
        if (!['CAPACITY','ROOM','EQUIPMENT','OTHER'].includes(kind)) {
          throw new HttpError(400, 'Tipo de recurso inválido.', 'VALIDATION_ERROR');
        }
        const inserted = await client.query(
          `INSERT INTO business_resources(business_id,name,kind) VALUES($1,$2,$3)
           RETURNING id,business_id,name,kind,status`, [businessId,name,kind]
        );
        return inserted.rows[0];
      });
      json(response, 201, { data: result });
      return;
    }

    if (method === 'PATCH' && recordId) {
      const user = authUser(request);
      const body = await readJson(request);
      const status = requiredString(body.status, 'estado', 16);
      if (!['ACTIVE','DISABLED'].includes(status)) throw new HttpError(400, 'Estado inválido.', 'VALIDATION_ERROR');
      const result = await withTransaction(pool, user.id, async (client) => {
        await ensureMembership(client, businessId, user.id);
        if (status === 'DISABLED') {
          const column = collection === 'staff' ? 'assigned_staff_id' : 'assigned_resource_id';
          if (collection === 'resources') {
            const alternatives = await client.query(
              `SELECT count(*)::int AS other_resources
               FROM business_resources WHERE business_id=$1 AND status='ACTIVE' AND id<>$2`,
              [businessId,recordId]
            );
            if (alternatives.rows[0].other_resources === 0) {
              throw new HttpError(409, 'El negocio debe conservar al menos un recurso activo para aceptar citas.', 'LAST_ACTIVE_RESOURCE');
            }
          }
          const future = await client.query(
            `SELECT 1 FROM reservations WHERE ${column}=$1 AND mode='CITA'
             AND status NOT IN ('CANCELADA','NO_SHOW') AND ends_at > now() LIMIT 1`, [recordId]
          );
          if (future.rowCount) {
            throw new HttpError(409, 'No se puede desactivar: tiene citas futuras o en curso. Reasígnalas o cancélalas primero.', 'ASSIGNEE_HAS_RESERVATIONS');
          }
        }
        const updated = await client.query(
          `UPDATE ${table} SET status=$1,updated_at=now() WHERE business_id=$2 AND id=$3
           RETURNING id,business_id,name,status`, [status,businessId,recordId]
        );
        if (!updated.rowCount) throw new HttpError(404, 'No se encontró el registro.', 'ASSIGNEE_NOT_FOUND');
        return updated.rows[0];
      });
      json(response, 200, { data: result });
      return;
    }
  }

  const businessMatch = /^\/api\/v1\/businesses\/([^/]+)(?:\/(listings|reservations|hours))?$/.exec(path);
  if (businessMatch) {
    const businessId = businessMatch[1];
    const section = businessMatch[2] || null;

    if (method === 'GET' && !section) {
      const user = request.headers.authorization ? authUser(request) : null;
      const result = await withTransaction(pool, user?.id || null, (client) => client.query(
        `SELECT b.id,b.name,b.description,b.phone,b.department,b.province,b.district,b.address,
          c.name AS category_name,c.emoji,
          COALESCE((SELECT array_agg(bm.mode ORDER BY bm.mode) FROM business_modes bm WHERE bm.business_id=b.id),'{}') AS modes,
          (SELECT s.status FROM subscriptions s WHERE s.business_id=b.id ORDER BY s.created_at DESC LIMIT 1) AS subscription_status,
          (SELECT s.ends_at FROM subscriptions s WHERE s.business_id=b.id ORDER BY s.created_at DESC LIMIT 1) AS trial_ends_at
         FROM businesses b JOIN categories c ON c.id=b.category_id WHERE b.id=$1 AND (is_public_business(b.id) OR b.owner_user_id=$2 OR is_business_member(b.id))`,
        [businessId,user?.id || null]
      ));
      if (!result.rowCount) throw new HttpError(404, 'Negocio no encontrado o no disponible.', 'BUSINESS_NOT_FOUND');
      json(response, 200, { data: asBusiness(result.rows[0]) });
      return;
    }

    if (section === 'listings' && method === 'GET') {
      const user = request.headers.authorization ? authUser(request) : null;
      const result = await withTransaction(pool, user?.id || null, (client) => client.query(
        `SELECT id,business_id,title,description,kind,mode,price,compare_at_price,duration_minutes,stock
         FROM listings WHERE business_id=$1 AND active=true ORDER BY created_at DESC`, [businessId]
      ));
      json(response, 200, { data: result.rows });
      return;
    }

    if (section === 'listings' && method === 'POST') {
      const user = authUser(request);
      const payload = validateListingPayload(await readJson(request));
      const result = await withTransaction(pool, user.id, async (client) => {
        await ensureMembership(client, businessId, user.id);
        await ensureBusinessSubscription(client, businessId);
        const modesResult = await client.query('SELECT mode FROM business_modes WHERE business_id=$1', [businessId]);
        const modes = new Set(modesResult.rows.map((row) => row.mode));
        if ((payload.mode === 'AMBOS' && !(modes.has('CITA') && modes.has('RECOJO'))) ||
            (payload.mode !== 'AMBOS' && !modes.has(payload.mode))) {
          throw new HttpError(400, 'La publicación usa una modalidad que el negocio no tiene habilitada.', 'MODE_NOT_AVAILABLE');
        }
        const inserted = await client.query(
          `INSERT INTO listings(business_id,title,description,kind,mode,price,compare_at_price,duration_minutes,stock)
           VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9)
           RETURNING id,business_id,title,description,kind,mode,price,compare_at_price,duration_minutes,stock,active`,
          [businessId,payload.title,payload.description,payload.kind,payload.mode,payload.price,payload.compareAtPrice,payload.durationMinutes,payload.stock]
        );
        return inserted.rows[0];
      });
      json(response, 201, { data: result });
      return;
    }

    if (section === 'reservations' && method === 'GET') {
      const user = authUser(request);
      const result = await withTransaction(pool, user.id, async (client) => {
        await ensureMembership(client, businessId, user.id, ['OWNER','MANAGER','STAFF']);
        return client.query(
          `SELECT r.id,r.code,r.mode,r.status,r.payment_method,r.payment_state,r.quantity,r.unit_price,r.total,r.note,
            COALESCE(r.starts_at,r.pickup_at) AS scheduled_at,u.full_name AS customer_name,u.phone AS customer_phone,l.title AS listing_title,
            COALESCE(s.name,br.name,'Sin asignación') AS assignee_name,
            CASE WHEN r.assigned_staff_id IS NOT NULL THEN 'TRABAJADOR' WHEN r.assigned_resource_id IS NOT NULL THEN 'RECURSO' ELSE NULL END AS assignee_type
           FROM reservations r JOIN users u ON u.id=r.customer_user_id JOIN listings l ON l.id=r.listing_id
           LEFT JOIN business_staff s ON s.id=r.assigned_staff_id
           LEFT JOIN business_resources br ON br.id=r.assigned_resource_id
           WHERE r.business_id=$1 ORDER BY r.created_at DESC LIMIT 200`, [businessId]
        );
      });
      json(response, 200, { data: result.rows });
      return;
    }

    if (section === 'hours' && method === 'GET') {
      const user = request.headers.authorization ? authUser(request) : null;
      const result = await withTransaction(pool, user?.id || null, (client) => client.query(
        `SELECT day_of_week,opens_at,closes_at,active FROM business_hours WHERE business_id=$1 ORDER BY day_of_week,opens_at`, [businessId]
      ));
      json(response, 200, { data: result.rows });
      return;
    }

    if (section === 'hours' && method === 'PUT') {
      const user = authUser(request);
      const body = await readJson(request);
      if (!Array.isArray(body.hours) || body.hours.length > 28) throw new HttpError(400, 'Envía una lista válida de horarios.', 'VALIDATION_ERROR');
      const hours = body.hours.map((h) => {
        const day = Number(h.dayOfWeek);
        const opens = requiredString(h.opensAt, 'hora de apertura', 8);
        const closes = requiredString(h.closesAt, 'hora de cierre', 8);
        if (!Number.isInteger(day) || day < 1 || day > 7 || !/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/.test(opens) || !/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/.test(closes) || opens >= closes) {
          throw new HttpError(400, 'Hay un horario con día u horas no válidos.', 'VALIDATION_ERROR');
        }
        return { day, opens, closes, active: h.active !== false };
      });
      await withTransaction(pool, user.id, async (client) => {
        await ensureMembership(client, businessId, user.id);
        await client.query('DELETE FROM business_hours WHERE business_id=$1', [businessId]);
        for (const h of hours) await client.query(
          'INSERT INTO business_hours(business_id,day_of_week,opens_at,closes_at,active) VALUES($1,$2,$3,$4,$5)',
          [businessId,h.day,h.opens,h.closes,h.active]
        );
      });
      json(response, 200, { message: 'Horario del negocio actualizado.', data: hours });
      return;
    }
  }

  const reservationStatusMatch = /^\/api\/v1\/businesses\/([^/]+)\/reservations\/([^/]+)\/status$/.exec(path);
  if ((method === 'PATCH' || method === 'POST') && reservationStatusMatch) {
    const user = authUser(request);
    const businessId = reservationStatusMatch[1];
    const reservationId = reservationStatusMatch[2];
    const body = await readJson(request);
    const nextStatus = requiredString(body.status, 'estado', 16);
    const result = await withTransaction(pool, user.id, async (client) => {
      await ensureMembership(client, businessId, user.id, ['OWNER','MANAGER','STAFF']);
      const current = await client.query('SELECT id,status,mode,quantity,listing_id FROM reservations WHERE id=$1 AND business_id=$2 FOR UPDATE', [reservationId,businessId]);
      if (!current.rowCount) throw new HttpError(404, 'No se encontró la reserva.', 'RESERVATION_NOT_FOUND');
      if (!canTransitionReservation(current.rows[0].status, nextStatus)) throw new HttpError(409, 'Ese cambio de estado no está permitido.', 'INVALID_STATE_TRANSITION');
      const updated = await client.query(
        'UPDATE reservations SET status=$1,updated_at=now() WHERE id=$2 RETURNING id,code,status', [nextStatus,reservationId]
      );
      return updated.rows[0];
    });
    json(response, 200, { data: result });
    return;
  }

  const cancelMatch = /^\/api\/v1\/me\/reservations\/([^/]+)\/cancel$/.exec(path);
  if (method === 'POST' && cancelMatch) {
    const user = authUser(request);
    const reservationId = cancelMatch[1];
    const result = await withTransaction(pool, user.id, async (client) => {
      const current = await client.query('SELECT id,status,mode,quantity,listing_id FROM reservations WHERE id=$1 AND customer_user_id=$2 FOR UPDATE', [reservationId,user.id]);
      if (!current.rowCount) throw new HttpError(404, 'No se encontró tu reserva.', 'RESERVATION_NOT_FOUND');
      if (!canTransitionReservation(current.rows[0].status, 'CANCELADA')) throw new HttpError(409, 'Esta reserva ya no puede cancelarse.', 'INVALID_STATE_TRANSITION');
      const updated = await client.query('UPDATE reservations SET status=\'CANCELADA\',updated_at=now() WHERE id=$1 RETURNING id,code,status', [reservationId]);
      return updated.rows[0];
    });
    json(response, 200, { data: result });
    return;
  }

  if (method === 'POST' && path === '/api/v1/businesses') {
    const user = authUser(request);
    const payload = validateBusinessPayload(await readJson(request));
    const result = await withTransaction(pool, user.id, async (client) => {
      const business = await createBusinessRecord(client, user.id, payload);
      return business;
    });
    json(response, 201, { data: result });
    return;
  }

  json(response, 404, { error: { code: 'NOT_FOUND', message: 'Ruta no encontrada.' } });
}

const server = createServer(async (request, response) => {
  try {
    await handle(request, response);
  } catch (error) {
    const mapped = mapPgError(error);
    if (mapped.status >= 500) {
      // No se devuelve SQL, trazas ni secretos al cliente.
      process.stderr.write(`[api] ${new Date().toISOString()} ${error?.code || error?.name || 'ERROR'} ${error?.message || ''}\n`);
    }
    if (!response.headersSent) json(response, mapped.status, { error: { code: mapped.code, message: mapped.message } });
    else response.destroy();
  }
});

server.listen(PORT, '0.0.0.0', () => {
  process.stdout.write(`UNAMAD Citas API escuchando en puerto ${PORT}\n`);
});

async function shutdown(signal) {
  process.stdout.write(`${signal}: cerrando API...\n`);
  server.close(async () => {
    await pool.end();
    process.exit(0);
  });
  setTimeout(() => process.exit(1), 10_000).unref();
}
process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));

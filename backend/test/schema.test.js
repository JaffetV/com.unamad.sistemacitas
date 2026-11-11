import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const sql = await readFile(new URL('../db/002_marketplace.sql', import.meta.url), 'utf8');
const appointmentsMigration = await readFile(new URL('../db/003_appointment_resources_idempotency.sql', import.meta.url), 'utf8');
const paginationMigration = await readFile(new URL('../db/004_marketplace_pagination.sql', import.meta.url), 'utf8');
const bootstrap = await readFile(new URL('../db/001_bootstrap.sh', import.meta.url), 'utf8');
const server = await readFile(new URL('../src/server.js', import.meta.url), 'utf8');

test('esquema protege datos privados mediante RLS forzado', () => {
  for (const table of ['users','businesses','business_members','subscriptions','business_modes','listings','business_hours','reservations','payments','favorites']) {
    assert.match(sql, new RegExp(`ALTER TABLE ${table} ENABLE ROW LEVEL SECURITY;\\s*ALTER TABLE ${table} FORCE ROW LEVEL SECURITY;`), `${table} debe tener RLS habilitado y forzado`);
  }
});

test('migración cambia exclusión global por exclusiones independientes de trabajador y recurso', () => {
  assert.match(appointmentsMigration, /DROP CONSTRAINT IF EXISTS reservations_no_appointment_overlap/);
  assert.match(appointmentsMigration, /reservations_no_staff_appointment_overlap/);
  assert.match(appointmentsMigration, /assigned_staff_id WITH =/);
  assert.match(appointmentsMigration, /reservations_no_resource_appointment_overlap/);
  assert.match(appointmentsMigration, /assigned_resource_id WITH =/);
  assert.doesNotMatch(appointmentsMigration, /EXCLUDE USING gist \(business_id WITH =, tstzrange\(starts_at,ends_at/);
});

test('migración agrega idempotencia persistente y evita duplicados por cliente', () => {
  assert.match(appointmentsMigration, /idempotency_key varchar\(128\)/);
  assert.match(appointmentsMigration, /request_fingerprint char\(64\)/);
  assert.match(appointmentsMigration, /reservations_customer_idempotency_uidx/);
  assert.match(appointmentsMigration, /UNIQUE INDEX IF NOT EXISTS reservations_customer_idempotency_uidx/);
});

test('migración conserva reservas anteriores y crea recursos de capacidad por negocio', () => {
  assert.match(appointmentsMigration, /INSERT INTO business_resources/);
  assert.match(appointmentsMigration, /UPDATE reservations r[\s\S]*assigned_resource_id/);
  assert.match(appointmentsMigration, /ON DELETE RESTRICT/);
  assert.match(appointmentsMigration, /appointment_assignee_available/);
});

test('migración protege trabajadores y recursos con RLS', () => {
  assert.match(appointmentsMigration, /ALTER TABLE business_staff ENABLE ROW LEVEL SECURITY/);
  assert.match(appointmentsMigration, /ALTER TABLE business_resources ENABLE ROW LEVEL SECURITY/);
  assert.match(appointmentsMigration, /business_staff_create/);
  assert.match(appointmentsMigration, /business_resources_create/);
});

test('la función de disponibilidad solo revela disponibilidad y respeta el rol runtime RLS', () => {
  assert.match(appointmentsMigration, /CREATE OR REPLACE FUNCTION appointment_assignee_available[\s\S]*RETURNS boolean[\s\S]*SECURITY DEFINER/);
  assert.match(appointmentsMigration, /SET search_path = pg_catalog, public, pg_temp/);
  assert.match(appointmentsMigration, /ALTER TABLE reservations NO FORCE ROW LEVEL SECURITY/);
  assert.match(appointmentsMigration, /REVOKE ALL ON FUNCTION appointment_assignee_available[\s\S]*FROM PUBLIC/);
  assert.match(bootstrap, /NOBYPASSRLS/);
});

test('el backend bloquea por clave idempotente antes de consultar una reserva previa', () => {
  const lockPosition = server.indexOf("await lockAssignee(client, 'idempotency'");
  const previousPosition = server.indexOf('SELECT id,code,status,payment_state,total,request_fingerprint', lockPosition);
  assert.ok(lockPosition >= 0, 'debe existir un lock por cliente y clave');
  assert.ok(previousPosition > lockPosition, 'el lock debe preceder la búsqueda del replay');
  assert.doesNotMatch(server, /ON CONFLICT \(customer_user_id,idempotency_key\)/);
  assert.match(server, /El advisory lock \+ la consulta previa resuelven los reintentos/);
  assert.match(server, /IDEMPOTENCY_KEY_REUSED/);
  assert.match(server, /Access-Control-Allow-Headers', 'Authorization, Content-Type, Idempotency-Key'/);
});

test('una cita en recurso legado también bloquea temporalmente el turno de un nuevo trabajador', () => {
  assert.match(appointmentsMigration, /p_type='STAFF' AND EXISTS[\s\S]*assigned_resource_id IS NOT NULL/);
  assert.match(server, /No se puede desactivar: tiene citas futuras o en curso/);
  assert.match(server, /LAST_ACTIVE_RESOURCE/);
});


test('disponibilidad usa fecha local, duración del servicio y turnos de 15 minutos', () => {
  assert.match(server, /AT TIME ZONE time_zone/);
  assert.match(server, /make_interval\(mins => w\.duration_minutes\)/);
  assert.match(server, /interval '15 minutes'/);
  assert.match(server, /p\.starts_at > now\(\)/);
  assert.match(server, /appointment_assignee_available\(p\.assignee_type,p\.assignee_id/);
  assert.match(server, /bm\.mode='CITA'/);
});

test('un negocio con trabajadores activos no acepta reservas manuales en el recurso general', () => {
  assert.match(server, /if \(resourceId && !staffId && staffCount\.rows\[0\]\.count > 0\)/);
  assert.match(server, /STAFF_REQUIRED/);
  assert.match(server, /pg_try_advisory_xact_lock/);
});

test('esquema incluye trial de 14 días y tarifas aún no fijadas', () => {
  assert.match(sql, /\('TRIAL','Prueba'.*?,0,14\)/);
  assert.match(sql, /\('BASIC'.*?NULL,0\)/);
  assert.match(sql, /\('PRO'.*?NULL,0\)/);
});

test('inventario se descuenta y devuelve con triggers transaccionales', () => {
  assert.match(sql, /CREATE TRIGGER reservations_decrement_stock\s+BEFORE INSERT ON reservations/);
  assert.match(sql, /CREATE TRIGGER reservations_restore_stock\s+AFTER UPDATE OF status ON reservations/);
  assert.match(sql, /RAISE EXCEPTION 'STOCK_UNAVAILABLE'/);
});



test('el catálogo del marketplace usa paginación keyset y orden estable', () => {
  assert.match(server, /decodeBusinessCursor/);
  assert.match(server, /parsePageSize/);
  assert.match(server, /\(b\.created_at,b\.id\) < \(\$4::timestamptz,\$5::uuid\)/);
  assert.match(server, /ORDER BY b\.created_at DESC,b\.id DESC LIMIT \$6/);
  assert.match(server, /nextCursor/);
});

test('la migración agrega índices parciales para el catálogo público', () => {
  assert.match(paginationMigration, /businesses_marketplace_cursor_idx/);
  assert.match(paginationMigration, /businesses_marketplace_category_cursor_idx/);
  assert.match(paginationMigration, /businesses_marketplace_district_cursor_idx/);
  assert.match(paginationMigration, /CREATE EXTENSION IF NOT EXISTS pg_trgm/);
  assert.match(paginationMigration, /businesses_marketplace_name_trgm_idx/);
  assert.match(paginationMigration, /businesses_marketplace_description_trgm_idx/);
});

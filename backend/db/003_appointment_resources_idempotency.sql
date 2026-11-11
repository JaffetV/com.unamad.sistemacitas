-- Migración incremental: disponibilidad por trabajador/recurso e idempotencia.
-- No elimina reservas ni datos históricos. Ejecutar después de 002_marketplace.sql.

CREATE TABLE IF NOT EXISTS business_staff (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id uuid NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
  name varchar(120) NOT NULL,
  specialty varchar(160) NOT NULL DEFAULT '',
  status varchar(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DISABLED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (business_id, id)
);
CREATE INDEX IF NOT EXISTS business_staff_active_idx
  ON business_staff(business_id, status, name);

CREATE TABLE IF NOT EXISTS business_resources (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  business_id uuid NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
  name varchar(120) NOT NULL,
  kind varchar(20) NOT NULL DEFAULT 'CAPACITY'
    CHECK (kind IN ('CAPACITY','ROOM','EQUIPMENT','OTHER')),
  is_default boolean NOT NULL DEFAULT false,
  status varchar(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DISABLED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (business_id, id)
);
CREATE INDEX IF NOT EXISTS business_resources_active_idx
  ON business_resources(business_id, status, name);

ALTER TABLE business_resources ADD COLUMN IF NOT EXISTS is_default boolean NOT NULL DEFAULT false;

ALTER TABLE reservations
  ADD COLUMN IF NOT EXISTS assigned_staff_id uuid,
  ADD COLUMN IF NOT EXISTS assigned_resource_id uuid,
  ADD COLUMN IF NOT EXISTS idempotency_key varchar(128),
  ADD COLUMN IF NOT EXISTS request_fingerprint char(64);

-- Cada negocio ya existente recibe al menos un puesto de atención.
INSERT INTO business_resources(business_id, name, kind, is_default, status)
SELECT b.id, 'Atención general 1', 'CAPACITY', true, 'ACTIVE'
FROM businesses b
WHERE NOT EXISTS (
  SELECT 1 FROM business_resources br WHERE br.business_id = b.id
);

-- Las reservas históricas estaban serializadas por negocio; se asignan al primer puesto
-- para conservar su significado y permitir que la nueva restricción opere por recurso.
UPDATE reservations r
SET assigned_resource_id = (
  SELECT br.id FROM business_resources br
  WHERE br.business_id = r.business_id
  ORDER BY br.created_at, br.id
  LIMIT 1
)
WHERE r.mode = 'CITA'
  AND r.assigned_staff_id IS NULL
  AND r.assigned_resource_id IS NULL;

ALTER TABLE reservations
  ADD CONSTRAINT reservations_assigned_staff_fk
    FOREIGN KEY (business_id, assigned_staff_id)
    REFERENCES business_staff(business_id, id) ON DELETE RESTRICT,
  ADD CONSTRAINT reservations_assigned_resource_fk
    FOREIGN KEY (business_id, assigned_resource_id)
    REFERENCES business_resources(business_id, id) ON DELETE RESTRICT;

ALTER TABLE reservations
  ADD CONSTRAINT reservations_appointment_assignment_check
    CHECK (mode <> 'CITA' OR assigned_staff_id IS NOT NULL OR assigned_resource_id IS NOT NULL),
  ADD CONSTRAINT reservations_idempotency_pair_check
    CHECK ((idempotency_key IS NULL AND request_fingerprint IS NULL)
        OR (idempotency_key IS NOT NULL AND request_fingerprint IS NOT NULL));

CREATE UNIQUE INDEX IF NOT EXISTS reservations_customer_idempotency_uidx
  ON reservations(customer_user_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS reservations_staff_calendar_idx
  ON reservations(assigned_staff_id, starts_at)
  WHERE mode = 'CITA' AND status NOT IN ('CANCELADA','NO_SHOW') AND assigned_staff_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS reservations_resource_calendar_idx
  ON reservations(assigned_resource_id, starts_at)
  WHERE mode = 'CITA' AND status NOT IN ('CANCELADA','NO_SHOW') AND assigned_resource_id IS NOT NULL;

-- Se reemplaza el bloqueo por negocio por bloqueos independientes por persona o puesto.
ALTER TABLE reservations DROP CONSTRAINT IF EXISTS reservations_no_appointment_overlap;
ALTER TABLE reservations ADD CONSTRAINT reservations_no_staff_appointment_overlap
  EXCLUDE USING gist (
    business_id WITH =,
    assigned_staff_id WITH =,
    tstzrange(starts_at, ends_at, '[)') WITH &&
  )
  WHERE (mode = 'CITA' AND status NOT IN ('CANCELADA','NO_SHOW') AND assigned_staff_id IS NOT NULL);
ALTER TABLE reservations ADD CONSTRAINT reservations_no_resource_appointment_overlap
  EXCLUDE USING gist (
    business_id WITH =,
    assigned_resource_id WITH =,
    tstzrange(starts_at, ends_at, '[)') WITH &&
  )
  WHERE (mode = 'CITA' AND status NOT IN ('CANCELADA','NO_SHOW') AND assigned_resource_id IS NOT NULL);

-- Esta función expone solo disponibilidad booleana, nunca datos del cliente o de la reserva.
-- Se ejecuta como propietario de la tabla para comprobar citas de otros clientes sin ampliar el RLS.
ALTER TABLE reservations NO FORCE ROW LEVEL SECURITY;
CREATE OR REPLACE FUNCTION appointment_assignee_available(
  p_type text, p_assignee_id uuid, p_starts_at timestamptz, p_ends_at timestamptz
) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, public, pg_temp AS $$
  SELECT NOT EXISTS (
    SELECT 1 FROM reservations r
    WHERE r.mode='CITA' AND r.status NOT IN ('CANCELADA','NO_SHOW')
      AND tstzrange(r.starts_at,r.ends_at,'[)') && tstzrange(p_starts_at,p_ends_at,'[)')
      AND ((p_type='STAFF' AND r.assigned_staff_id=p_assignee_id)
        OR (p_type='RESOURCE' AND r.assigned_resource_id=p_assignee_id))
  )
  AND NOT (
    p_type='STAFF' AND EXISTS (
      -- Si se incorpora personal después de operar con puestos virtuales,
      -- las citas históricas/futuras que sigan en esos puestos también ocupan
      -- capacidad del negocio durante la transición y no deben solaparse con
      -- una cita nueva de un trabajador.
      SELECT 1 FROM reservations r
      WHERE r.business_id=(SELECT s.business_id FROM business_staff s WHERE s.id=p_assignee_id)
        AND r.mode='CITA' AND r.status NOT IN ('CANCELADA','NO_SHOW')
        AND r.assigned_resource_id IS NOT NULL
        AND tstzrange(r.starts_at,r.ends_at,'[)') && tstzrange(p_starts_at,p_ends_at,'[)')
    )
  )
$$;
REVOKE ALL ON FUNCTION appointment_assignee_available(text,uuid,timestamptz,timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION appointment_assignee_available(text,uuid,timestamptz,timestamptz) TO marketplace_app;

ALTER TABLE business_staff ENABLE ROW LEVEL SECURITY;
ALTER TABLE business_staff FORCE ROW LEVEL SECURITY;
CREATE POLICY business_staff_read ON business_staff FOR SELECT
  USING (is_business_member(business_id) OR is_public_business(business_id));
CREATE POLICY business_staff_create ON business_staff FOR INSERT
  WITH CHECK (is_business_member(business_id, ARRAY['OWNER','MANAGER']));
CREATE POLICY business_staff_manage ON business_staff FOR UPDATE
  USING (is_business_member(business_id, ARRAY['OWNER','MANAGER']))
  WITH CHECK (is_business_member(business_id, ARRAY['OWNER','MANAGER']));

ALTER TABLE business_resources ENABLE ROW LEVEL SECURITY;
ALTER TABLE business_resources FORCE ROW LEVEL SECURITY;
CREATE POLICY business_resources_read ON business_resources FOR SELECT
  USING (is_business_member(business_id) OR is_public_business(business_id));
CREATE POLICY business_resources_create ON business_resources FOR INSERT
  WITH CHECK (is_business_member(business_id, ARRAY['OWNER','MANAGER']));
CREATE POLICY business_resources_manage ON business_resources FOR UPDATE
  USING (is_business_member(business_id, ARRAY['OWNER','MANAGER']))
  WITH CHECK (is_business_member(business_id, ARRAY['OWNER','MANAGER']));

GRANT SELECT, INSERT, UPDATE, DELETE ON business_staff, business_resources TO marketplace_app;
GRANT SELECT, INSERT, UPDATE ON reservations TO marketplace_app;

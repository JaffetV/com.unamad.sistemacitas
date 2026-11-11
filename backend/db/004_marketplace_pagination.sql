-- Índices para exploración por cursor y filtros frecuentes del marketplace.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Se usan índices normales para que esta migración también pueda ejecutarse
-- dentro de una transacción administrada por el proceso de despliegue.

CREATE INDEX IF NOT EXISTS businesses_marketplace_cursor_idx
    ON businesses (created_at DESC, id DESC)
    WHERE state = 'ACTIVE' AND visible_marketplace = true;

CREATE INDEX IF NOT EXISTS businesses_marketplace_category_cursor_idx
    ON businesses (category_id, created_at DESC, id DESC)
    WHERE state = 'ACTIVE' AND visible_marketplace = true;

CREATE INDEX IF NOT EXISTS businesses_marketplace_district_cursor_idx
    ON businesses (district, created_at DESC, id DESC)
    WHERE state = 'ACTIVE' AND visible_marketplace = true;

COMMENT ON INDEX businesses_marketplace_cursor_idx IS
    'Orden estable y paginación keyset del catálogo público.';

-- Búsqueda parcial por nombre/descripción (la API usa ILIKE con comodines).
CREATE INDEX IF NOT EXISTS businesses_marketplace_name_trgm_idx
    ON businesses USING GIN (name gin_trgm_ops)
    WHERE state = 'ACTIVE' AND visible_marketplace = true;

CREATE INDEX IF NOT EXISTS businesses_marketplace_description_trgm_idx
    ON businesses USING GIN (description gin_trgm_ops)
    WHERE state = 'ACTIVE' AND visible_marketplace = true;

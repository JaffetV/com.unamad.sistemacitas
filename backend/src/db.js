import pg from 'pg';

const { Pool } = pg;

export function createPool(connectionString) {
  const maxConnections = Number(process.env.PG_POOL_MAX || 15);
  if (!Number.isInteger(maxConnections) || maxConnections < 1 || maxConnections > 100) {
    throw new Error('PG_POOL_MAX debe ser un entero entre 1 y 100.');
  }
  return new Pool({
    connectionString,
    max: maxConnections,
    idleTimeoutMillis: 30_000,
    connectionTimeoutMillis: 5_000,
    application_name: 'unamad-citas-marketplace-api',
    ssl: process.env.PGSSL === 'true' ? { rejectUnauthorized: true } : undefined
  });
}

export async function withTransaction(pool, userId, work) {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    await client.query("SELECT set_config('app.user_id', $1, true)", [userId || '']);
    const result = await work(client);
    await client.query('COMMIT');
    return result;
  } catch (error) {
    await client.query('ROLLBACK').catch(() => {});
    throw error;
  } finally {
    client.release();
  }
}

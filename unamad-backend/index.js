const express = require('express');
const { Pool } = require('pg');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());

// Configuración de conexión a la Base de Datos
const pool = new Pool({
  host: process.env.DB_HOST || 'localhost',
  user: process.env.DB_USER || 'citas_user',
  password: process.env.DB_PASSWORD || 'citas_password',
  database: process.env.DB_NAME || 'db_citas',
  port: process.env.DB_PORT || 5432,
});

// Inicializar la tabla en PostgreSQL
const initDB = async () => {
  try {
    await pool.query(`
      CREATE TABLE IF NOT EXISTS citas (
        id SERIAL PRIMARY KEY,
        codigo_estudiante VARCHAR(50),
        nombre_estudiante VARCHAR(100),
        escuela VARCHAR(100),
        fecha VARCHAR(50),
        hora VARCHAR(50),
        motivo TEXT,
        estado VARCHAR(20) DEFAULT 'Pendiente'
      )
    `);
    console.log('Tabla "citas" verificada o creada en la base de datos.');
  } catch (err) {
    console.error('Error al crear la tabla:', err.message);
  }
};

// Conectar con retardo para permitir que PostgreSQL inicie primero
setTimeout(initDB, 4000);

// GET: Consultar citas en la Base de Datos
app.get('/api/citas', async (req, res) => {
  try {
    const result = await pool.query('SELECT * FROM citas ORDER BY id DESC');
    res.json(result.rows);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// POST: Registrar cita en la Base de Datos
app.post('/api/citas', async (req, res) => {
  const { codigoEstudiante, nombreEstudiante, escuela, fecha, hora, motivo } = req.body;
  try {
    const result = await pool.query(
      `INSERT INTO citas (codigo_estudiante, nombre_estudiante, escuela, fecha, hora, motivo)
       VALUES ($1, $2, $3, $4, $5, $6) RETURNING *`,
      [codigoEstudiante, nombreEstudiante, escuela, fecha, hora, motivo]
    );
    res.status(201).json(result.rows[0]);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// DELETE: Eliminar cita de la Base de Datos
app.delete('/api/citas/:id', async (req, res) => {
  const { id } = req.params;
  try {
    await pool.query('DELETE FROM citas WHERE id = $1', [id]);
    res.json({ mensaje: `Cita con ID ${id} eliminada` });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.listen(PORT, () => {
  console.log(`Servidor de Citas corriendo en el puerto ${PORT}`);
});
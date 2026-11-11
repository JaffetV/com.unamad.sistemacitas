/**
 * Servidor Backend Principal - Sistema de Citas UNAMAD
 * Arquitectura Fullstack: API REST para App Android (Kotlin) & Panel Web (HTML/JS)
 * Autor: Ingeniería de Software & DevOps UNAMAD
 */

const express = require('express');
const cors = require('cors');
const { Pool } = require('pg');

const app = express();
const PORT = process.env.PORT || 3000;

// --- 1. MIDDLEWARES DE CONFIGURACIÓN ---
app.use(cors());
app.use(express.json());
app.use(express.static('public'));

// --- 2. CONFIGURACIÓN DE CONEXIÓN A POSTGRESQL ---
const pool = new Pool({
  host: process.env.DB_HOST || 'localhost',
  user: process.env.DB_USER || 'citas_user',
  password: process.env.DB_PASSWORD || 'citas_password',
  database: process.env.DB_NAME || 'db_citas',
  port: process.env.DB_PORT ? parseInt(process.env.DB_PORT, 10) : 5432,
});

// --- 3. INICIALIZACIÓN DE LA BASE DE DATOS Y SEED DATA ---
const initDB = async () => {
  try {
    // Creación de tabla si no existe
    await pool.query(`
      CREATE TABLE IF NOT EXISTS citas (
        id SERIAL PRIMARY KEY,
        codigo_estudiante VARCHAR(50) NOT NULL,
        nombre_estudiante VARCHAR(100) NOT NULL,
        escuela VARCHAR(100) NOT NULL,
        fecha VARCHAR(50) NOT NULL,
        hora VARCHAR(50) NOT NULL,
        motivo TEXT NOT NULL,
        estado VARCHAR(20) DEFAULT 'Pendiente'
      )
    `);
    console.log('✔ Tabla "citas" verificada o creada correctamente en PostgreSQL.');

    // Verificación y poblado automático de datos de prueba (Seed Data) si está vacía
    const countResult = await pool.query('SELECT COUNT(*) FROM citas');
    const totalCitas = parseInt(countResult.rows[0].count, 10);

    if (totalCitas === 0) {
      console.log('📌 La tabla "citas" está vacía. Insertando registros de prueba de la UNAMAD...');
      const sampleData = [
        ['2021012345', 'Carlos Mendoza Quispe', 'Ingeniería de Sistemas y Tecnologías de la Información', '2026-06-15', '09:00', 'Asesoría de tesis y revisión de avance del proyecto de investigación', 'Pendiente'],
        ['2022054321', 'Lucía Fernández Ramos', 'Derecho y Ciencias Políticas', '2026-06-15', '10:30', 'Consulta sobre convalidación de cursos y trámites de matrícula extemporánea', 'Atendido'],
        ['2023098765', 'Diego Huamán Salazar', 'Ingeniería Forestal y Medio Ambiente', '2026-06-16', '15:00', 'Tutoría académica sobre ecología tropical y prácticas preprofesionales', 'Pendiente']
      ];

      for (const row of sampleData) {
        await pool.query(
          `INSERT INTO citas (codigo_estudiante, nombre_estudiante, escuela, fecha, hora, motivo, estado)
           VALUES ($1, $2, $3, $4, $5, $6, $7)`,
          row
        );
      }
      console.log('✔ Registros de prueba insertados exitosamente.');
    } else {
      console.log(`ℹ La base de datos ya contiene ${totalCitas} registros.`);
    }
  } catch (err) {
    console.error('❌ Error al inicializar o poblar la base de datos:', err.message);
  }
};

// Reconexión diferida para dar tiempo a que PostgreSQL (unamad_db) complete su arranque en Docker
setTimeout(initDB, 3500);

// --- 4. ENDPOINTS API REST ---

/**
 * GET /api/citas
 * Devuelve todas las citas registradas ordenadas por ID de forma descendente.
 */
app.get('/api/citas', async (req, res) => {
  try {
    const result = await pool.query('SELECT * FROM citas ORDER BY id DESC');
    res.json(result.rows);
  } catch (err) {
    console.error('Error en GET /api/citas:', err.message);
    res.status(500).json({ error: 'Error interno al consultar las citas: ' + err.message });
  }
});

/**
 * POST /api/citas
 * Valida los campos obligatorios y registra una nueva cita en el sistema.
 */
app.post('/api/citas', async (req, res) => {
  const { codigoEstudiante, nombreEstudiante, escuela, fecha, hora, motivo, estado } = req.body;

  // 1. Validación de campos obligatorios
  if (!codigoEstudiante || !nombreEstudiante || !escuela || !fecha || !hora || !motivo) {
    return res.status(400).json({ error: 'Todos los campos son obligatorios' });
  }

  try {
    const estadoFinal = estado || 'Pendiente';
    const result = await pool.query(
      `INSERT INTO citas (codigo_estudiante, nombre_estudiante, escuela, fecha, hora, motivo, estado)
       VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING *`,
      [codigoEstudiante, nombreEstudiante, escuela, fecha, hora, motivo, estadoFinal]
    );
    res.status(201).json(result.rows[0]);
  } catch (err) {
    console.error('Error en POST /api/citas:', err.message);
    res.status(500).json({ error: 'Error interno al registrar la cita: ' + err.message });
  }
});

/**
 * PUT /api/citas/:id
 * Actualiza los datos o el estado de una cita existente (ej. cambiar a 'Atendido' o 'Cancelado').
 */
app.put('/api/citas/:id', async (req, res) => {
  const { id } = req.params;
  const { codigoEstudiante, nombreEstudiante, escuela, fecha, hora, motivo, estado } = req.body;

  try {
    const result = await pool.query(
      `UPDATE citas
       SET codigo_estudiante = COALESCE($1, codigo_estudiante),
           nombre_estudiante = COALESCE($2, nombre_estudiante),
           escuela = COALESCE($3, escuela),
           fecha = COALESCE($4, fecha),
           hora = COALESCE($5, hora),
           motivo = COALESCE($6, motivo),
           estado = COALESCE($7, estado)
       WHERE id = $8 RETURNING *`,
      [codigoEstudiante, nombreEstudiante, escuela, fecha, hora, motivo, estado, id]
    );

    // Si la cita no existe, retorna código 404 (Not Found)
    if (result.rows.length === 0) {
      return res.status(404).json({ error: `No se encontró la cita con ID ${id}` });
    }

    res.json(result.rows[0]);
  } catch (err) {
    console.error(`Error en PUT /api/citas/${id}:`, err.message);
    res.status(500).json({ error: 'Error interno al actualizar la cita: ' + err.message });
  }
});

/**
 * DELETE /api/citas/:id
 * Elimina una cita existente por su identificador único.
 */
app.delete('/api/citas/:id', async (req, res) => {
  const { id } = req.params;

  try {
    const result = await pool.query('DELETE FROM citas WHERE id = $1 RETURNING *', [id]);

    // Si la cita no existe, retorna código 404 (Not Found)
    if (result.rows.length === 0) {
      return res.status(404).json({ error: `No se encontró la cita con ID ${id} para eliminar` });
    }

    res.json({
      mensaje: `Cita con ID ${id} eliminada correctamente`,
      citaEliminada: result.rows[0]
    });
  } catch (err) {
    console.error(`Error en DELETE /api/citas/${id}:`, err.message);
    res.status(500).json({ error: 'Error interno al eliminar la cita: ' + err.message });
  }
});

// --- 5. ARRANQUE DEL SERVIDOR ---
app.listen(PORT, () => {
  console.log(`🚀 Servidor Backend UNAMAD Citas operando en el puerto ${PORT}`);
});

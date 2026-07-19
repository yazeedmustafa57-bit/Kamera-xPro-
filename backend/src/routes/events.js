const express = require('express');
const { Pool } = require('pg');
const { v4: uuidv4 } = require('uuid');
const authMiddleware = require('../middleware/authMiddleware');

const router = express.Router();
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

// Alle Events des Users (nur eigene Kameras)
router.get('/', authMiddleware, async (req, res) => {
  try {
    const result = await pool.query(`
      SELECT e.*, c.name as camera_name 
      FROM events e 
      JOIN cameras c ON e.camera_id = c.id 
      WHERE c.owner_id = $1 
      ORDER BY e.created_at DESC 
      LIMIT 100
    `, [req.user.id]);
    res.json(result.rows);
  } catch (err) {
    res.status(500).json({ error: 'Server-Fehler' });
  }
});

// Event erstellen
router.post('/', authMiddleware, async (req, res) => {
  try {
    const { camera_id, type, thumbnail_url, video_url } = req.body;
    if (!camera_id || !type) return res.status(400).json({ error: 'camera_id und type erforderlich' });

    // Pruefen ob Kamera dem User gehoert
    const camCheck = await pool.query(
      'SELECT id FROM cameras WHERE id = $1 AND owner_id = $2',
      [camera_id, req.user.id]
    );
    if (camCheck.rows.length === 0) return res.status(403).json({ error: 'Kein Zugriff' });

    const result = await pool.query(
      'INSERT INTO events (id, camera_id, type, thumbnail_url, video_url) VALUES ($1, $2, $3, $4, $5) RETURNING *',
      [uuidv4(), camera_id, type, thumbnail_url || null, video_url || null]
    );
    res.status(201).json(result.rows[0]);
  } catch (err) {
    res.status(500).json({ error: 'Server-Fehler' });
  }
});

module.exports = router;

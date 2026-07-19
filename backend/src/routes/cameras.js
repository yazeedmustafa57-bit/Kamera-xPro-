const express = require('express');
const { Pool } = require('pg');
const { v4: uuidv4 } = require('uuid');
const authMiddleware = require('../middleware/authMiddleware');

const router = express.Router();
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

// Alle Kameras des eingeloggten Users
router.get('/', authMiddleware, async (req, res) => {
  try {
    const result = await pool.query(
      'SELECT * FROM cameras WHERE owner_id = $1 ORDER BY created_at DESC',
      [req.user.id]
    );
    res.json(result.rows);
  } catch (err) {
    res.status(500).json({ error: 'Server-Fehler' });
  }
});

// Kamera erstellen
router.post('/', authMiddleware, async (req, res) => {
  try {
    const { name, device_model } = req.body;
    if (!name) return res.status(400).json({ error: 'Name erforderlich' });

    const pairing_code = Math.random().toString(36).substring(2, 8).toUpperCase();

    const result = await pool.query(
      'INSERT INTO cameras (id, owner_id, name, device_model, pairing_code) VALUES ($1, $2, $3, $4, $5) RETURNING *',
      [uuidv4(), req.user.id, name, device_model || 'Android', pairing_code]
    );
    res.status(201).json(result.rows[0]);
  } catch (err) {
    res.status(500).json({ error: 'Server-Fehler' });
  }
});

// Einzelne Kamera
router.get('/:cameraId', authMiddleware, async (req, res) => {
  try {
    const result = await pool.query(
      'SELECT * FROM cameras WHERE id = $1 AND owner_id = $2',
      [req.params.cameraId, req.user.id]
    );
    if (result.rows.length === 0) return res.status(404).json({ error: 'Kamera nicht gefunden' });
    res.json(result.rows[0]);
  } catch (err) {
    res.status(500).json({ error: 'Server-Fehler' });
  }
});

// Kamera aktualisieren
router.put('/:cameraId', authMiddleware, async (req, res) => {
  try {
    const { name, status } = req.body;
    const result = await pool.query(
      'UPDATE cameras SET name = COALESCE($1, name), status = COALESCE($2, status), last_seen = now() WHERE id = $3 AND owner_id = $4 RETURNING *',
      [name, status, req.params.cameraId, req.user.id]
    );
    if (result.rows.length === 0) return res.status(404).json({ error: 'Kamera nicht gefunden' });
    res.json(result.rows[0]);
  } catch (err) {
    res.status(500).json({ error: 'Server-Fehler' });
  }
});

// Kamera loeschen
router.delete('/:cameraId', authMiddleware, async (req, res) => {
  try {
    const result = await pool.query(
      'DELETE FROM cameras WHERE id = $1 AND owner_id = $2 RETURNING id',
      [req.params.cameraId, req.user.id]
    );
    if (result.rows.length === 0) return res.status(404).json({ error: 'Kamera nicht gefunden' });
    res.json({ message: 'Kamera geloescht' });
  } catch (err) {
    res.status(500).json({ error: 'Server-Fehler' });
  }
});

// Kamera per Pairing-Code verbinden
router.post('/pair', authMiddleware, async (req, res) => {
  try {
    const { pairing_code } = req.body;
    if (!pairing_code) return res.status(400).json({ error: 'Pairing-Code erforderlich' });

    const result = await pool.query(
      'SELECT * FROM cameras WHERE pairing_code = $1',
      [pairing_code]
    );
    if (result.rows.length === 0) return res.status(404).json({ error: 'Ungueltiger Code' });

    const camera = result.rows[0];
    if (camera.owner_id === req.user.id) {
      return res.json(camera);
    }

    // Neuer Owner
    const updated = await pool.query(
      'UPDATE cameras SET owner_id = $1, pairing_code = NULL WHERE id = $2 RETURNING *',
      [req.user.id, camera.id]
    );
    res.json(updated.rows[0]);
  } catch (err) {
    res.status(500).json({ error: 'Server-Fehler' });
  }
});

module.exports = router;

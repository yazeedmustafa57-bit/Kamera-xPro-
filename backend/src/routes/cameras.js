const express = require('express');
const { v4: uuidv4 } = require('uuid');
const authMiddleware = require('../middleware/authMiddleware');
const db = require('../db');

const router = express.Router();

router.get('/', authMiddleware, (req, res) => {
  res.json(db.getCamerasByOwner(req.user.id));
});

router.post('/', authMiddleware, (req, res) => {
  const { name } = req.body;
  if (!name) return res.status(400).json({ error: 'Name erforderlich' });

  const camera = {
    id: uuidv4(), owner_id: req.user.id, name, device_model: 'Android',
    status: 'offline', pairing_code: Math.random().toString(36).substring(2, 8).toUpperCase(),
    created_at: Date.now()
  };
  db.addCamera(camera);
  res.status(201).json(camera);
});

router.get('/:id', authMiddleware, (req, res) => {
  const cam = db.findCameraById(req.params.id);
  if (!cam || cam.owner_id !== req.user.id) return res.status(404).json({ error: 'Nicht gefunden' });
  res.json(cam);
});

router.put('/:id', authMiddleware, (req, res) => {
  const cam = db.updateCamera(req.params.id, req.body);
  if (!cam || cam.owner_id !== req.user.id) return res.status(404).json({ error: 'Nicht gefunden' });
  res.json(cam);
});

router.delete('/:id', authMiddleware, (req, res) => {
  const cam = db.findCameraById(req.params.id);
  if (!cam || cam.owner_id !== req.user.id) return res.status(404).json({ error: 'Nicht gefunden' });
  db.deleteCamera(req.params.id);
  res.json({ message: 'Geloescht' });
});

router.post('/pair', authMiddleware, (req, res) => {
  const { pairing_code } = req.body;
  if (!pairing_code) return res.status(400).json({ error: 'Code erforderlich' });
  const cam = db.findCameraByPairingCode(pairing_code);
  if (!cam) return res.status(404).json({ error: 'Code ungueltig' });
  db.updateCamera(cam.id, { owner_id: req.user.id, pairing_code: null });
  res.json(db.findCameraById(cam.id));
});

module.exports = router;

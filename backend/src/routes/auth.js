const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { v4: uuidv4 } = require('uuid');
const db = require('../db');

const router = express.Router();
const JWT_SECRET = process.env.JWT_SECRET || 'smartcampro-secret-key-2024';

function generateTokens(user) {
  return {
    accessToken: jwt.sign({ id: user.id, email: user.email }, JWT_SECRET, { expiresIn: '30d' }),
    refreshToken: jwt.sign({ id: user.id, type: 'refresh' }, JWT_SECRET + '_refresh', { expiresIn: '90d' })
  };
}

router.post('/register', (req, res) => {
  try {
    const { email, password, display_name } = req.body;
    if (!email || !password) return res.status(400).json({ error: 'E-Mail und Passwort erforderlich' });
    if (password.length < 6) return res.status(400).json({ error: 'Passwort mindestens 6 Zeichen' });
    if (db.findUserByEmail(email)) return res.status(400).json({ error: 'E-Mail bereits registriert' });

    const user = {
      id: uuidv4(), email, display_name: display_name || email.split('@')[0],
      password_hash: bcrypt.hashSync(password, 10),
      subscription_tier: 'free', created_at: Date.now()
    };
    db.addUser(user);
    const tokens = generateTokens(user);
    res.status(201).json({
      user: { id: user.id, email: user.email, display_name: user.display_name, subscription_tier: user.subscription_tier },
      ...tokens
    });
  } catch (err) { res.status(500).json({ error: 'Server-Fehler' }); }
});

router.post('/login', (req, res) => {
  try {
    const { email, password } = req.body;
    if (!email || !password) return res.status(400).json({ error: 'E-Mail und Passwort erforderlich' });
    const user = db.findUserByEmail(email);
    if (!user || !bcrypt.compareSync(password, user.password_hash)) {
      return res.status(401).json({ error: 'Falsche Anmeldedaten' });
    }
    const tokens = generateTokens(user);
    res.json({
      user: { id: user.id, email: user.email, display_name: user.display_name, subscription_tier: user.subscription_tier },
      ...tokens
    });
  } catch (err) { res.status(500).json({ error: 'Server-Fehler' }); }
});

router.post('/refresh', (req, res) => {
  try {
    const { refresh_token } = req.body;
    if (!refresh_token) return res.status(400).json({ error: 'Refresh Token erforderlich' });
    const decoded = jwt.verify(refresh_token, JWT_SECRET + '_refresh');
    const user = db.findUserById(decoded.id);
    if (!user) return res.status(401).json({ error: 'User nicht gefunden' });
    res.json({ user: { id: user.id, email: user.email, display_name: user.display_name, subscription_tier: user.subscription_tier }, ...generateTokens(user) });
  } catch (err) { return res.status(401).json({ error: 'Token abgelaufen' }); }
});

module.exports = router;

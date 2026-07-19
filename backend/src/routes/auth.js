const express = require('express');
const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { Pool } = require('pg');
const { v4: uuidv4 } = require('uuid');

const router = express.Router();
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

function generateTokens(user) {
  const accessToken = jwt.sign(
    { id: user.id, email: user.email },
    process.env.JWT_SECRET,
    { expiresIn: process.env.ACCESS_TOKEN_EXPIRY || '15m' }
  );
  const refreshToken = jwt.sign(
    { id: user.id, type: 'refresh' },
    process.env.JWT_REFRESH_SECRET,
    { expiresIn: process.env.REFRESH_TOKEN_EXPIRY || '30d' }
  );
  return { accessToken, refreshToken };
}

// Register
router.post('/register', async (req, res) => {
  try {
    const { email, password, display_name } = req.body;
    
    if (!email || !password) {
      return res.status(400).json({ error: 'E-Mail und Passwort erforderlich' });
    }
    if (password.length < 8) {
      return res.status(400).json({ error: 'Passwort mindestens 8 Zeichen' });
    }

    // Check existing
    const existing = await pool.query('SELECT id FROM users WHERE email = $1', [email]);
    if (existing.rows.length > 0) {
      return res.status(400).json({ error: 'E-Mail bereits registriert' });
    }

    // Hash password
    const password_hash = await bcrypt.hash(password, 12);

    // Create user
    const result = await pool.query(
      'INSERT INTO users (id, email, password_hash, display_name) VALUES ($1, $2, $3, $4) RETURNING id, email, display_name, subscription_tier, created_at',
      [uuidv4(), email, password_hash, display_name || email.split('@')[0]]
    );

    const user = result.rows[0];
    const tokens = generateTokens(user);

    res.status(201).json({
      user: {
        id: user.id,
        email: user.email,
        display_name: user.display_name,
        subscription_tier: user.subscription_tier,
        created_at: user.created_at
      },
      ...tokens
    });
  } catch (err) {
    console.error('Register error:', err);
    res.status(500).json({ error: 'Server-Fehler' });
  }
});

// Login
router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({ error: 'E-Mail und Passwort erforderlich' });
    }

    const result = await pool.query(
      'SELECT id, email, password_hash, display_name, subscription_tier FROM users WHERE email = $1',
      [email]
    );

    if (result.rows.length === 0) {
      return res.status(401).json({ error: 'Falsche Anmeldedaten' });
    }

    const user = result.rows[0];
    const valid = await bcrypt.compare(password, user.password_hash);

    if (!valid) {
      return res.status(401).json({ error: 'Falsche Anmeldedaten' });
    }

    const tokens = generateTokens(user);

    res.json({
      user: {
        id: user.id,
        email: user.email,
        display_name: user.display_name,
        subscription_tier: user.subscription_tier
      },
      ...tokens
    });
  } catch (err) {
    console.error('Login error:', err);
    res.status(500).json({ error: 'Server-Fehler' });
  }
});

// Refresh token
router.post('/refresh', async (req, res) => {
  try {
    const { refresh_token } = req.body;
    if (!refresh_token) {
      return res.status(400).json({ error: 'Refresh Token erforderlich' });
    }

    const decoded = jwt.verify(refresh_token, process.env.JWT_REFRESH_SECRET);
    if (decoded.type !== 'refresh') {
      return res.status(401).json({ error: 'Ungueltiger Token' });
    }

    const result = await pool.query('SELECT id, email, display_name, subscription_tier FROM users WHERE id = $1', [decoded.id]);
    if (result.rows.length === 0) {
      return res.status(401).json({ error: 'User nicht gefunden' });
    }

    const user = result.rows[0];
    const tokens = generateTokens(user);

    res.json({
      user: {
        id: user.id,
        email: user.email,
        display_name: user.display_name,
        subscription_tier: user.subscription_tier
      },
      ...tokens
    });
  } catch (err) {
    return res.status(401).json({ error: 'Token abgelaufen' });
  }
});

// Get current user
router.get('/me', async (req, res) => {
  const authHeader = req.headers.authorization;
  if (!authHeader) return res.status(401).json({ error: 'Nicht authentifiziert' });

  try {
    const token = authHeader.split(' ')[1];
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    const result = await pool.query(
      'SELECT id, email, display_name, subscription_tier, created_at FROM users WHERE id = $1',
      [decoded.id]
    );
    if (result.rows.length === 0) return res.status(404).json({ error: 'User nicht gefunden' });
    res.json(result.rows[0]);
  } catch (err) {
    res.status(401).json({ error: 'Ungueltiger Token' });
  }
});

module.exports = router;

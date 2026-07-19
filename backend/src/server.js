require('dotenv').config();
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const { createServer } = require('http');
const { Server } = require('socket.io');
const { Pool } = require('pg');

const app = express();
const server = createServer(app);
const io = new Server(server, {
  cors: { origin: process.env.CORS_ORIGIN || '*', methods: ['GET', 'POST'] }
});

// Database
const pool = new Pool({ connectionString: process.env.DATABASE_URL });
pool.on('error', (err) => console.error('Database error:', err));

// Middleware
app.use(helmet());
app.use(cors({ origin: process.env.CORS_ORIGIN || '*', credentials: true }));
app.use(express.json({ limit: '10mb' }));

// Rate limiting
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 100,
  message: { error: 'Zu viele Anfragen. Bitte spaeter erneut versuchen.' }
});
app.use('/api/', limiter);

const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 10,
  message: { error: 'Zu viele Login-Versuche. Bitte 15 Minuten warten.' }
});
app.use('/api/auth/login', authLimiter);
app.use('/api/auth/register', authLimiter);

// Routes
const authRoutes = require('./routes/auth');
const cameraRoutes = require('./routes/cameras');
const eventRoutes = require('./routes/events');

app.use('/api/auth', authRoutes);
app.use('/api/cameras', cameraRoutes);
app.use('/api/events', eventRoutes);

// Socket.IO signaling
const { setupSignaling } = require('./sockets/signaling');
setupSignaling(io, pool);

// Health check
app.get('/api/health', (req, res) => {
  res.json({ status: 'healthy', version: '2.0.0', service: 'SmartCam Pro API' });
});

app.get('/', (req, res) => {
  res.json({ name: 'SmartCam Pro API', version: '2.0.0', docs: '/api/health' });
});

// Initialize database
async function initDB() {
  const client = await pool.connect();
  try {
    const fs = require('fs');
    const sql = fs.readFileSync(__dirname + '/database.sql', 'utf8');
    await client.query(sql);
    console.log('Database initialized');
  } catch (err) {
    console.error('Database init error:', err.message);
  } finally {
    client.release();
  }
}

const PORT = process.env.PORT || 3000;
initDB().then(() => {
  server.listen(PORT, () => {
    console.log(`SmartCam Pro Server running on port ${PORT}`);
  });
});

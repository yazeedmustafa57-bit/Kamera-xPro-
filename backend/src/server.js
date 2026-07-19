const express = require('express');
const cors = require('cors');
const { createServer } = require('http');
const { Server } = require('socket.io');
const fs = require('fs');

const app = express();
const httpServer = createServer(app);
const io = new Server(httpServer, { cors: { origin: '*', methods: ['GET', 'POST'] } });

app.use(cors());
app.use(express.json());

const DB_FILE = __dirname + '/../data.json';
if (!fs.existsSync(DB_FILE)) { fs.writeFileSync(DB_FILE, JSON.stringify({ users: [], cameras: [], events: [] })); }

const db = require('./db');
const authMiddleware = require('./middleware/authMiddleware');
const { setupSignaling } = require('./sockets/signaling');

// Health
app.get('/api/health', (req, res) => res.json({ status: 'healthy', version: '3.0.0', service: 'SmartCam Pro', users: db.getUsers().length, cameras: db.getCameras().length }));
app.get('/', (req, res) => res.json({ name: 'SmartCam Pro API', version: '3.0.0', health: '/api/health' }));

// Routes
app.use('/api/auth', require('./routes/auth'));
app.use('/api/cameras', require('./routes/cameras'));
app.get('/api/events/', authMiddleware, (req, res) => { res.json(db.getEventsByOwner(req.user.id)); });

// Socket.IO
setupSignaling(io);

const PORT = process.env.PORT || 3000;
httpServer.listen(PORT, '0.0.0.0', () => {
  console.log(`\nSmartCam Pro Server v3.0 running on port ${PORT}\n`);
});

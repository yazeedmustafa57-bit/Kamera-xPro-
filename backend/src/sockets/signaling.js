const jwt = require('jsonwebtoken');
const JWT_SECRET = process.env.JWT_SECRET || 'smartcampro-secret-key-2024';
const db = require('../db');

function setupSignaling(io) {
  io.use((socket, next) => {
    const token = socket.handshake.auth.token;
    if (!token) return next(new Error('Nicht authentifiziert'));
    try {
      const decoded = jwt.verify(token, JWT_SECRET);
      socket.userId = decoded.id;
      next();
    } catch (err) { next(new Error('Token ungueltig')); }
  });

  io.on('connection', (socket) => {
    console.log(`Connected: ${socket.userId}`);

    socket.on('camera:join', (cameraId) => {
      const cam = db.findCameraById(cameraId);
      if (cam && cam.owner_id === socket.userId) {
        socket.join(`camera:${cameraId}`);
        socket.cameraId = cameraId;
        db.updateCamera(cameraId, { status: 'online' });
        io.to(`watchers:${cameraId}`).emit('camera:status', { cameraId, status: 'online' });
        console.log(`Camera ${cameraId} joined`);
      }
    });

    socket.on('watcher:join', (cameraId) => {
      const cam = db.findCameraById(cameraId);
      if (cam && cam.owner_id === socket.userId) {
        socket.join(`watchers:${cameraId}`);
        socket.watchingCameraId = cameraId;
        io.to(`camera:${cameraId}`).emit('watcher:joined', { watcherId: socket.id });
        console.log(`Watcher ${socket.id} joined camera ${cameraId}`);
      }
    });

    socket.on('watcher:leave', (cameraId) => {
      socket.leave(`watchers:${cameraId}`);
      io.to(`camera:${cameraId}`).emit('watcher:left', { watcherId: socket.id });
      console.log(`Watcher ${socket.id} left camera ${cameraId}`);
    });

    socket.on('camera:motion', (data) => {
      const { cameraId, type } = data;
      const cam = db.findCameraById(cameraId);
      if (cam && cam.owner_id === socket.userId) {
        db.addEvent({ id: require('uuid').v4(), camera_id: cameraId, type: type || 'motion', created_at: Date.now() });
        io.to(`watchers:${cameraId}`).emit('camera:motion', { cameraId, type, timestamp: Date.now() });
        console.log(`Motion on ${cameraId}: ${type}`);
      }
    });

    socket.on('camera:alarm', (data) => {
      const { cameraId, message } = data;
      const cam = db.findCameraById(cameraId);
      if (cam && cam.owner_id === socket.userId) {
        db.addEvent({ id: require('uuid').v4(), camera_id: cameraId, type: 'alarm', created_at: Date.now() });
        io.to(`watchers:${cameraId}`).emit('camera:alarm', { cameraId, message: message || 'Alarm ausgeloest!', timestamp: Date.now() });
        console.log(`Alarm on ${cameraId}`);
      }
    });

    socket.on('disconnect', () => {
      if (socket.cameraId) {
        db.updateCamera(socket.cameraId, { status: 'offline' });
        io.to(`watchers:${socket.cameraId}`).emit('camera:status', { cameraId: socket.cameraId, status: 'offline' });
        console.log(`Camera ${socket.cameraId} disconnected`);
      }
      if (socket.watchingCameraId) {
        io.to(`camera:${socket.watchingCameraId}`).emit('watcher:left', { watcherId: socket.id });
        console.log(`Watcher ${socket.id} disconnected from camera ${socket.watchingCameraId}`);
      }
    });
  });
}

module.exports = { setupSignaling };

const jwt = require('jsonwebtoken');

function setupSignaling(io, pool) {
  // Auth middleware for Socket.IO
  io.use(async (socket, next) => {
    const token = socket.handshake.auth.token;
    if (!token) return next(new Error('Nicht authentifiziert'));

    try {
      const decoded = jwt.verify(token, process.env.JWT_SECRET);
      socket.userId = decoded.id;
      socket.userEmail = decoded.email;
      next();
    } catch (err) {
      next(new Error('Ungueltiger Token'));
    }
  });

  io.on('connection', (socket) => {
    console.log(`User connected: ${socket.userEmail} (${socket.userId})`);

    // Camera joins its room
    socket.on('camera:join', async (cameraId) => {
      try {
        // Verify ownership
        const result = await pool.query(
          'SELECT id FROM cameras WHERE id = $1 AND owner_id = $2',
          [cameraId, socket.userId]
        );
        if (result.rows.length === 0) {
          socket.emit('error', { message: 'Kein Zugriff auf diese Kamera' });
          return;
        }

        socket.join(`camera:${cameraId}`);
        socket.cameraId = cameraId;

        // Update camera status to online
        await pool.query(
          'UPDATE cameras SET status = $1, last_seen = now() WHERE id = $2',
          ['online', cameraId]
        );

        // Notify all viewers
        io.to(`watchers:${cameraId}`).emit('camera:status', {
          cameraId,
          status: 'online'
        });

        console.log(`Camera ${cameraId} joined room`);
      } catch (err) {
        console.error('Camera join error:', err);
      }
    });

    // Viewer watches a camera
    socket.on('watcher:join', async (cameraId) => {
      try {
        // Verify ownership
        const result = await pool.query(
          'SELECT id FROM cameras WHERE id = $1 AND owner_id = $2',
          [cameraId, socket.userId]
        );
        if (result.rows.length === 0) {
          socket.emit('error', { message: 'Kein Zugriff auf diese Kamera' });
          return;
        }

        socket.join(`watchers:${cameraId}`);
        socket.watchingCameraId = cameraId;

        // Tell camera a watcher joined
        io.to(`camera:${cameraId}`).emit('watcher:joined', {
          watcherId: socket.id
        });

        console.log(`Watcher ${socket.userEmail} watching camera ${cameraId}`);
      } catch (err) {
        console.error('Watcher join error:', err);
      }
    });

    // WebRTC signaling
    socket.on('webrtc:offer', (data) => {
      io.to(`camera:${data.cameraId}`).emit('webrtc:offer', {
        offer: data.offer,
        watcherId: socket.id
      });
    });

    socket.on('webrtc:answer', (data) => {
      io.to(`watchers:${data.cameraId}`).emit('webrtc:answer', {
        answer: data.answer,
        watcherId: data.watcherId
      });
    });

    socket.on('webrtc:ice-candidate', (data) => {
      if (data.targetCamera) {
        io.to(`camera:${data.cameraId}`).emit('webrtc:ice-candidate', {
          candidate: data.candidate,
          watcherId: socket.id
        });
      } else {
        io.to(`watchers:${data.cameraId}`).emit('webrtc:ice-candidate', {
          candidate: data.candidate,
          watcherId: data.watcherId
        });
      }
    });

    // Camera events
    socket.on('camera:motion', async (data) => {
      try {
        const { cameraId, type, thumbnailUrl } = data;
        if (!cameraId) return;

        // Verify ownership
        const camCheck = await pool.query(
          'SELECT id, owner_id FROM cameras WHERE id = $1 AND owner_id = $2',
          [cameraId, socket.userId]
        );
        if (camCheck.rows.length === 0) return;

        // Save event
        const { v4: uuidv4 } = require('uuid');
        await pool.query(
          'INSERT INTO events (id, camera_id, type, thumbnail_url) VALUES ($1, $2, $3, $4)',
          [uuidv4(), cameraId, type || 'motion', thumbnailUrl || null]
        );

        // Notify all viewers
        io.to(`watchers:${cameraId}`).emit('camera:motion', {
          cameraId,
          type: type || 'motion',
          timestamp: new Date().toISOString()
        });

        // Push notification
        sendPushNotification(pool, camCheck.rows[0].owner_id, {
          title: 'Bewegung erkannt!',
          body: `Kamera hat Bewegung erkannt`,
          cameraId
        });
      } catch (err) {
        console.error('Motion event error:', err);
      }
    });

    // Disconnect
    socket.on('disconnect', async () => {
      console.log(`User disconnected: ${socket.userEmail}`);

      if (socket.cameraId) {
        await pool.query(
          'UPDATE cameras SET status = $1, last_seen = now() WHERE id = $2',
          ['offline', socket.cameraId]
        );

        io.to(`watchers:${socket.cameraId}`).emit('camera:status', {
          cameraId: socket.cameraId,
          status: 'offline'
        });
      }

      if (socket.watchingCameraId) {
        io.to(`camera:${socket.watchingCameraId}`).emit('watcher:left', {
          watcherId: socket.id
        });
      }
    });
  });
}

async function sendPushNotification(pool, userId, data) {
  try {
    const result = await pool.query(
      'SELECT fcm_token FROM push_tokens WHERE user_id = $1',
      [userId]
    );
    // FCM integration would go here
    // For now, just log
    if (result.rows.length > 0) {
      console.log(`Push to ${result.rows.length} devices:`, data.title);
    }
  } catch (err) {
    console.error('Push notification error:', err);
  }
}

module.exports = { setupSignaling };

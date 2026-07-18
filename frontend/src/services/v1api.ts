import axios from 'axios';

const api = axios.create({ baseURL: '/api' });
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    if (err.response?.status === 429) {
      console.warn('Rate limited. Retry after:', err.response.headers['retry-after']);
    }
    return Promise.reject(err);
  }
);

// WebRTC
export const webrtcAPI = {
  getRoomInfo: (cameraId: string) => api.get(`/v1/webrtc/rooms/${cameraId}`),
  listRooms: () => api.get('/v1/webrtc/rooms'),
};

// Recording
export const recordingAPI = {
  start: (cameraId: string, fps = 15) =>
    api.post(`/v1/recording/start/${cameraId}?fps=${fps}`),
  stop: (sessionId: string) => api.post(`/v1/recording/stop/${sessionId}`),
  getStorage: () => api.get('/v1/recording/storage'),
  cleanup: () => api.post('/v1/recording/cleanup'),
};

// AI
export const aiAPI = {
  getModelStatus: () => api.get('/v1/ai/model/status'),
  downloadModel: () => api.post('/v1/ai/model/download'),
  loadModel: () => api.post('/v1/ai/model/load'),
  detect: (cameraId: string, frameBase64: string) =>
    api.post(`/v1/ai/detect?camera_id=${cameraId}&frame_base64=${encodeURIComponent(frameBase64)}`),
  getDetectorConfig: () => api.get('/v1/ai/detector/config'),
  updateDetectorConfig: (config: { min_confidence?: number; target_classes?: string[]; active?: boolean }) =>
    api.put('/v1/ai/detector/config', null, { params: config }),
};

// Zones
export const zonesAPI = {
  getCameraZones: (cameraId: string) => api.get(`/v1/zones/${cameraId}`),
  createZone: (cameraId: string, data: { name: string; points: string; sensitivity?: number; target_classes?: string[]; cooldown_seconds?: number }) =>
    api.post(`/v1/zones/${cameraId}`, null, { params: data }),
  deleteZone: (zoneId: string) => api.delete(`/v1/zones/${zoneId}`),
};

// Audit
export const auditAPI = {
  getLogs: (params?: { action?: string; limit?: number }) =>
    api.get('/v1/audit/logs', { params }),
};

// Sessions
export const sessionsAPI = {
  getMySessions: () => api.get('/v1/sessions'),
  revokeSession: (id: string) => api.delete(`/v1/sessions/${id}`),
  revokeAll: () => api.delete('/v1/sessions/all'),
};

import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { camerasAPI, eventsAPI } from '../services/api';
import { recordingAPI } from '../services/v1api';
import { useWebSocket } from '../hooks/useWebSocket';
import { useAuth } from '../hooks/useAuth';
import type { Camera, Event } from '../types';
import WebRTCViewer from '../components/WebRTCViewer';
import ZoneEditor from '../components/ZoneEditor';
import {
  Camera as CameraIcon, ArrowLeft, Battery, Wifi, WifiOff,
  Mic, Volume2, Maximize, Camera as ScreenshotIcon, Video,
  RotateCcw, AlertTriangle, Clock, Square, Circle,
} from 'lucide-react';
import toast from 'react-hot-toast';

export default function CameraDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { subscribe } = useWebSocket(user?.id || null);
  const [camera, setCamera] = useState<Camera | null>(null);
  const [events, setEvents] = useState<Event[]>([]);
  const [micActive, setMicActive] = useState(false);
  const [speakerActive, setSpeakerActive] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [recordingSessionId, setRecordingSessionId] = useState<string | null>(null);
  const [showZones, setShowZones] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (id) loadCamera();
  }, [id]);

  useEffect(() => {
    const unsub = subscribe('camera_status', (msg) => {
      if (msg.camera_id === id) {
        setCamera((prev) => prev ? { ...prev, status: msg.status, battery: msg.battery, wifi_signal: msg.wifi_signal } : null);
      }
    });
    return unsub;
  }, [subscribe, id]);

  const loadCamera = async () => {
    try {
      const [camRes, eventsRes] = await Promise.all([
        camerasAPI.get(id!),
        eventsAPI.list({ camera_id: id, limit: 20 }),
      ]);
      setCamera(camRes.data);
      setEvents(eventsRes.data);
    } catch (err) { toast.error('Kamera nicht gefunden'); navigate('/cameras'); }
    setLoading(false);
  };

  const handleScreenshot = () => {
    toast.success('Screenshot gespeichert');
  };

  const handleToggleRecord = async () => {
    if (!id) return;
    if (isRecording && recordingSessionId) {
      try {
        const res = await recordingAPI.stop(recordingSessionId);
        toast.success(`Aufnahme gespeichert (${res.data.duration}s)`);
        setIsRecording(false);
        setRecordingSessionId(null);
      } catch { toast.error('Stop fehlgeschlagen'); }
    } else {
      try {
        const res = await recordingAPI.start(id);
        toast.success('Aufnahme gestartet');
        setIsRecording(true);
        setRecordingSessionId(res.data.session_id);
      } catch { toast.error('Start fehlgeschlagen'); }
    }
  };

  if (loading || !camera) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-2 border-accent-blue border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-4">
        <button onClick={() => navigate('/cameras')} className="text-dark-400 hover:text-white transition-colors">
          <ArrowLeft className="w-5 h-5" />
        </button>
        <div className="flex-1">
          <h1 className="text-2xl font-bold text-white">{camera.name}</h1>
          <p className="text-dark-400">{camera.location || 'Kein Standort'}</p>
        </div>
        <span className={`badge ${camera.status === 'online' || camera.status === 'streaming' ? 'badge-green' : 'badge-red'}`}>
          {camera.status === 'online' || camera.status === 'streaming' ? '● Online' : '● Offline'}
        </span>
      </div>

      {/* WebRTC Live View */}
      <div className="card p-0 overflow-hidden">
        <WebRTCViewer cameraId={id!} cameraName={camera.name} />
      </div>

      {/* Controls */}
      <div className="card">
        <div className="flex items-center justify-center gap-3">
          <button
            onClick={() => setMicActive(!micActive)}
            className={`p-3 rounded-xl transition-all ${micActive ? 'bg-accent-blue text-white' : 'bg-dark-600 text-dark-300 hover:text-white'}`}
            title="Mikrofon"
          >
            <Mic className="w-5 h-5" />
          </button>
          <button
            onClick={() => setSpeakerActive(!speakerActive)}
            className={`p-3 rounded-xl transition-all ${speakerActive ? 'bg-accent-blue text-white' : 'bg-dark-600 text-dark-300 hover:text-white'}`}
            title="Lautsprecher"
          >
            <Volume2 className="w-5 h-5" />
          </button>
          <button onClick={handleScreenshot} className="p-3 rounded-xl bg-dark-600 text-dark-300 hover:text-white transition-all" title="Screenshot">
            <ScreenshotIcon className="w-5 h-5" />
          </button>
          <button
            onClick={handleToggleRecord}
            className={`p-3 rounded-xl transition-all ${isRecording ? 'bg-red-500 text-white animate-pulse' : 'bg-dark-600 text-dark-300 hover:text-white'}`}
            title={isRecording ? 'Aufnahme stoppen' : 'Aufnahme starten'}
          >
            {isRecording ? <Square className="w-5 h-5" /> : <Circle className="w-5 h-5" />}
          </button>
          <button
            onClick={() => setShowZones(!showZones)}
            className={`p-3 rounded-xl transition-all ${showZones ? 'bg-accent-purple text-white' : 'bg-dark-600 text-dark-300 hover:text-white'}`}
            title="Zonen bearbeiten"
          >
            <Video className="w-5 h-5" />
          </button>
        </div>

        {/* Status indicators */}
        <div className="flex items-center justify-center gap-6 mt-4 pt-4 border-t border-dark-600">
          <div className="flex items-center gap-2 text-sm">
            <Battery className={`w-4 h-4 ${camera.battery > 50 ? 'text-green-400' : 'text-amber-400'}`} />
            <span className="text-dark-300">{camera.battery}%</span>
          </div>
          <div className="flex items-center gap-2 text-sm">
            {camera.wifi_signal > 50 ? <Wifi className="w-4 h-4 text-green-400" /> : <WifiOff className="w-4 h-4 text-red-400" />}
            <span className="text-dark-300">{camera.wifi_signal}%</span>
          </div>
          {isRecording && (
            <div className="flex items-center gap-2 text-sm">
              <span className="w-2 h-2 bg-red-400 rounded-full animate-pulse" />
              <span className="text-red-400">Recording</span>
            </div>
          )}
        </div>
      </div>

      {/* Zone Editor */}
      {showZones && (
        <div className="card">
          <ZoneEditor cameraId={id!} />
        </div>
      )}

      {/* Events */}
      <div>
        <h2 className="text-lg font-semibold text-white mb-4">Letzte Ereignisse</h2>
        {events.length === 0 ? (
          <div className="card text-center py-8">
            <AlertTriangle className="w-10 h-10 text-dark-500 mx-auto mb-2" />
            <p className="text-dark-400">Keine Ereignisse für diese Kamera</p>
          </div>
        ) : (
          <div className="space-y-2">
            {events.map((event) => (
              <div key={event.id} className="card flex items-center gap-4">
                <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${
                  event.type === 'person' ? 'bg-blue-500/20' :
                  event.type === 'vehicle' ? 'bg-green-500/20' :
                  event.type === 'animal' ? 'bg-amber-500/20' : 'bg-purple-500/20'
                }`}>
                  <AlertTriangle className={`w-5 h-5 ${
                    event.type === 'person' ? 'text-blue-400' :
                    event.type === 'vehicle' ? 'text-green-400' :
                    event.type === 'animal' ? 'text-amber-400' : 'text-purple-400'
                  }`} />
                </div>
                <div className="flex-1">
                  <p className="text-sm font-medium text-white capitalize">{event.type} erkannt</p>
                  <p className="text-xs text-dark-400">Vertrauen: {Math.round(parseFloat(event.confidence) * 100)}%</p>
                </div>
                <p className="text-xs text-dark-400 flex items-center gap-1">
                  <Clock className="w-3 h-3" />
                  {new Date(event.created_at).toLocaleString('de-DE')}
                </p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { useWebSocket } from '../hooks/useWebSocket';
import { dashboardAPI, camerasAPI, eventsAPI } from '../services/api';
import type { Camera, Event, DashboardStats } from '../types';
import {
  Camera as CameraIcon, Activity, AlertTriangle, Video,
  Battery, Wifi, WifiOff, Eye, Clock, HardDrive, ChevronRight,
  Radio,
} from 'lucide-react';
import toast from 'react-hot-toast';

export default function DashboardPage() {
  const { user } = useAuth();
  const { connected, subscribe } = useWebSocket(user?.id || null);
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [cameras, setCameras] = useState<Camera[]>([]);
  const [recentEvents, setRecentEvents] = useState<Event[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, []);

  useEffect(() => {
    const unsub = subscribe('camera_status', (msg) => {
      setCameras((prev) =>
        prev.map((c) =>
          c.id === msg.camera_id
            ? { ...c, status: msg.status, battery: msg.battery, wifi_signal: msg.wifi_signal }
            : c
        )
      );
    });

    const unsubMotion = subscribe('motion_detected', (msg) => {
      toast(`🚨 Bewegung erkannt auf Kamera!`, { icon: '📷' });
      loadData();
    });

    return () => { unsub(); unsubMotion(); };
  }, [subscribe]);

  const loadData = async () => {
    try {
      const [statsRes, camerasRes, eventsRes] = await Promise.all([
        dashboardAPI.getStats(),
        camerasAPI.list(),
        eventsAPI.list({ limit: 10 }),
      ]);
      setStats(statsRes.data);
      setCameras(camerasRes.data);
      setRecentEvents(eventsRes.data);
    } catch (err) {
      console.error('Dashboard load error:', err);
    }
    setLoading(false);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="w-8 h-8 border-2 border-accent-blue border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  const statCards = [
    { label: 'Kameras Gesamt', value: stats?.total_cameras || 0, icon: CameraIcon, color: 'text-accent-blue', bg: 'bg-blue-500/10' },
    { label: 'Online', value: stats?.online_cameras || 0, icon: Radio, color: 'text-green-400', bg: 'bg-green-500/10' },
    { label: 'Alarme Heute', value: stats?.events_today || 0, icon: AlertTriangle, color: 'text-amber-400', bg: 'bg-amber-500/10' },
    { label: 'Aufnahmen', value: stats?.total_recordings || 0, icon: Video, color: 'text-purple-400', bg: 'bg-purple-500/10' },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Dashboard</h1>
          <p className="text-dark-400 mt-1">
            Willkommen zurück, <span className="text-accent-blue">{user?.username}</span>
          </p>
        </div>
        <div className="flex items-center gap-2">
          <div className={`w-2.5 h-2.5 rounded-full ${connected ? 'bg-green-400 animate-pulse' : 'bg-red-400'}`} />
          <span className="text-sm text-dark-400">{connected ? 'Verbunden' : 'Getrennt'}</span>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {statCards.map((stat) => (
          <div key={stat.label} className="card-hover">
            <div className="flex items-center gap-3">
              <div className={`w-12 h-12 ${stat.bg} rounded-xl flex items-center justify-center`}>
                <stat.icon className={`w-6 h-6 ${stat.color}`} />
              </div>
              <div>
                <p className="text-2xl font-bold text-white">{stat.value}</p>
                <p className="text-sm text-dark-400">{stat.label}</p>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Camera Grid */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-white">Live Kameras</h2>
          <Link to="/cameras" className="text-sm text-accent-blue hover:text-blue-400 flex items-center gap-1">
            Alle anzeigen <ChevronRight className="w-4 h-4" />
          </Link>
        </div>

        {cameras.length === 0 ? (
          <div className="card text-center py-12">
            <CameraIcon className="w-12 h-12 text-dark-500 mx-auto mb-3" />
            <p className="text-dark-400">Noch keine Kameras hinzugefügt</p>
            <Link to="/cameras" className="btn-primary mt-4 inline-flex">
              Kamera hinzufügen
            </Link>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {cameras.slice(0, 6).map((camera) => (
              <Link key={camera.id} to={`/cameras/${camera.id}`} className="card-hover group">
                {/* Camera preview */}
                <div className="relative aspect-video bg-dark-900 rounded-lg mb-3 overflow-hidden">
                  <div className="absolute inset-0 flex items-center justify-center">
                    <CameraIcon className="w-12 h-12 text-dark-600" />
                  </div>
                  {/* Status badge */}
                  <div className="absolute top-2 left-2">
                    <span className={`badge ${camera.status === 'online' || camera.status === 'streaming' ? 'badge-green' : 'badge-red'}`}>
                      {camera.status === 'online' || camera.status === 'streaming' ? '● Live' : '● Offline'}
                    </span>
                  </div>
                  {/* Recording indicator */}
                  {camera.status === 'recording' && (
                    <div className="absolute top-2 right-2">
                      <span className="badge badge-red animate-pulse">
                        <span className="w-1.5 h-1.5 bg-red-400 rounded-full mr-1" /> REC
                      </span>
                    </div>
                  )}
                </div>

                {/* Camera info */}
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="font-medium text-white group-hover:text-accent-blue transition-colors">
                      {camera.name}
                    </h3>
                    <p className="text-xs text-dark-400">{camera.location || 'Kein Standort'}</p>
                  </div>
                  <div className="flex items-center gap-3">
                    <div className="flex items-center gap-1 text-xs">
                      <Battery className={`w-3.5 h-3.5 ${camera.battery > 50 ? 'text-green-400' : camera.battery > 20 ? 'text-amber-400' : 'text-red-400'}`} />
                      <span className="text-dark-300">{camera.battery}%</span>
                    </div>
                    <div className="flex items-center gap-1 text-xs">
                      {camera.wifi_signal > 50 ? <Wifi className="w-3.5 h-3.5 text-green-400" /> : <WifiOff className="w-3.5 h-3.5 text-red-400" />}
                      <span className="text-dark-300">{camera.wifi_signal}%</span>
                    </div>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        )}
      </div>

      {/* Recent Events */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-white">Letzte Alarme</h2>
          <Link to="/alarms" className="text-sm text-accent-blue hover:text-blue-400 flex items-center gap-1">
            Alle anzeigen <ChevronRight className="w-4 h-4" />
          </Link>
        </div>

        {recentEvents.length === 0 ? (
          <div className="card text-center py-8">
            <Activity className="w-10 h-10 text-dark-500 mx-auto mb-2" />
            <p className="text-dark-400">Keine kürzlichen Alarme</p>
          </div>
        ) : (
          <div className="space-y-2">
            {recentEvents.slice(0, 5).map((event) => (
              <div key={event.id} className="card flex items-center gap-4">
                <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${
                  event.type === 'person' ? 'bg-blue-500/20' :
                  event.type === 'motion' ? 'bg-amber-500/20' : 'bg-purple-500/20'
                }`}>
                  <AlertTriangle className={`w-5 h-5 ${
                    event.type === 'person' ? 'text-blue-400' :
                    event.type === 'motion' ? 'text-amber-400' : 'text-purple-400'
                  }`} />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-white capitalize">{event.type} erkannt</p>
                  <p className="text-xs text-dark-400">{event.camera_name || 'Unbekannte Kamera'}</p>
                </div>
                <div className="text-right">
                  <p className="text-xs text-dark-400 flex items-center gap-1">
                    <Clock className="w-3 h-3" />
                    {new Date(event.created_at).toLocaleTimeString('de-DE')}
                  </p>
                  <p className="text-xs text-dark-500">{Math.round(parseFloat(event.confidence) * 100)}%</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Storage */}
      {stats && (
        <div className="card">
          <div className="flex items-center gap-3 mb-2">
            <HardDrive className="w-5 h-5 text-accent-cyan" />
            <h3 className="text-sm font-medium text-white">Speicher</h3>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-2xl font-bold text-white">{stats.storage_used}</span>
            <span className="text-sm text-dark-400">belegt</span>
          </div>
        </div>
      )}
    </div>
  );
}

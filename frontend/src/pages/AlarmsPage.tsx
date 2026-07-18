import { useState, useEffect } from 'react';
import { eventsAPI } from '../services/api';
import { useWebSocket } from '../hooks/useWebSocket';
import { useAuth } from '../hooks/useAuth';
import type { Event } from '../types';
import {
  Bell, AlertTriangle, Check, Trash2, Filter, Clock, Eye, User,
  Car, Cat, Activity,
} from 'lucide-react';
import toast from 'react-hot-toast';

export default function AlarmsPage() {
  const { user } = useAuth();
  const { subscribe } = useWebSocket(user?.id || null);
  const [events, setEvents] = useState<Event[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<string>('all');

  useEffect(() => { loadEvents(); }, [filter]);

  useEffect(() => {
    const unsub = subscribe('motion_detected', () => loadEvents());
    return unsub;
  }, [subscribe]);

  const loadEvents = async () => {
    try {
      const params: any = { limit: 100 };
      if (filter !== 'all') params.event_type = filter;
      const res = await eventsAPI.list(params);
      setEvents(res.data);
    } catch (err) { toast.error('Fehler'); }
    setLoading(false);
  };

  const markRead = async (id: string) => {
    try {
      await eventsAPI.markRead(id);
      setEvents((prev) => prev.map((e) => e.id === id ? { ...e, is_read: true } : e));
    } catch {}
  };

  const deleteEvent = async (id: string) => {
    try {
      await eventsAPI.delete(id);
      setEvents((prev) => prev.filter((e) => e.id !== id));
      toast.success('Gelöscht');
    } catch { toast.error('Fehler'); }
  };

  const getEventIcon = (type: string) => {
    switch (type) {
      case 'person': return <User className="w-5 h-5 text-blue-400" />;
      case 'car': return <Car className="w-5 h-5 text-green-400" />;
      case 'animal': return <Cat className="w-5 h-5 text-amber-400" />;
      default: return <Activity className="w-5 h-5 text-purple-400" />;
    }
  };

  const getEventBg = (type: string) => {
    switch (type) {
      case 'person': return 'bg-blue-500/20';
      case 'car': return 'bg-green-500/20';
      case 'animal': return 'bg-amber-500/20';
      default: return 'bg-purple-500/20';
    }
  };

  const filters = [
    { key: 'all', label: 'Alle' },
    { key: 'person', label: 'Personen' },
    { key: 'car', label: 'Fahrzeuge' },
    { key: 'animal', label: 'Tiere' },
    { key: 'motion', label: 'Bewegung' },
  ];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Alarme</h1>
          <p className="text-dark-400 mt-1">{events.filter((e) => !e.is_read).length} ungelesen</p>
        </div>
      </div>

      {/* Filter */}
      <div className="flex items-center gap-2 flex-wrap">
        {filters.map((f) => (
          <button
            key={f.key}
            onClick={() => setFilter(f.key)}
            className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-all ${
              filter === f.key ? 'bg-accent-blue text-white' : 'bg-dark-700 text-dark-300 hover:bg-dark-600'
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="w-8 h-8 border-2 border-accent-blue border-t-transparent rounded-full animate-spin" />
        </div>
      ) : events.length === 0 ? (
        <div className="card text-center py-16">
          <Bell className="w-16 h-16 text-dark-600 mx-auto mb-4" />
          <h3 className="text-lg font-medium text-white mb-2">Keine Alarme</h3>
          <p className="text-dark-400">Es wurden keine Ereignisse aufgezeichnet</p>
        </div>
      ) : (
        <div className="space-y-2">
          {events.map((event) => (
            <div
              key={event.id}
              className={`card flex items-center gap-4 transition-all ${
                !event.is_read ? 'border-accent-blue/30 bg-dark-700/80' : ''
              }`}
            >
              <div className={`w-11 h-11 rounded-xl flex items-center justify-center ${getEventBg(event.type)}`}>
                {getEventIcon(event.type)}
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <p className="text-sm font-medium text-white capitalize">{event.type} erkannt</p>
                  {!event.is_read && (
                    <span className="w-2 h-2 bg-accent-blue rounded-full" />
                  )}
                </div>
                <p className="text-xs text-dark-400 mt-0.5">
                  {event.camera_name || 'Kamera'} · {Math.round(parseFloat(event.confidence) * 100)}% Vertrauen
                </p>
              </div>
              <div className="flex items-center gap-3">
                <span className="text-xs text-dark-500 flex items-center gap-1">
                  <Clock className="w-3 h-3" />
                  {new Date(event.created_at).toLocaleString('de-DE')}
                </span>
                <div className="flex items-center gap-1">
                  {!event.is_read && (
                    <button
                      onClick={() => markRead(event.id)}
                      className="p-1.5 rounded-lg text-dark-400 hover:bg-green-500/20 hover:text-green-400 transition-colors"
                      title="Gelesen"
                    >
                      <Check className="w-4 h-4" />
                    </button>
                  )}
                  <button
                    onClick={() => deleteEvent(event.id)}
                    className="p-1.5 rounded-lg text-dark-400 hover:bg-red-500/20 hover:text-red-400 transition-colors"
                    title="Löschen"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

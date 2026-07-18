import { useState, useEffect } from 'react';
import { recordingsAPI } from '../services/api';
import type { Recording } from '../types';
import { Video, Trash2, Play, Clock, HardDrive, Calendar, X } from 'lucide-react';
import toast from 'react-hot-toast';

export default function RecordingsPage() {
  const [recordings, setRecordings] = useState<Recording[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedRecording, setSelectedRecording] = useState<Recording | null>(null);

  useEffect(() => { loadRecordings(); }, []);

  const loadRecordings = async () => {
    try {
      const res = await recordingsAPI.list({ limit: 100 });
      setRecordings(res.data);
    } catch (err) { toast.error('Fehler beim Laden'); }
    setLoading(false);
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Aufnahme wirklich löschen?')) return;
    try {
      await recordingsAPI.delete(id);
      toast.success('Gelöscht');
      loadRecordings();
    } catch (err) { toast.error('Fehler'); }
  };

  const handleDeleteAll = async () => {
    if (!confirm('Alle Aufnahmen wirklich löschen?')) return;
    try {
      await recordingsAPI.deleteAll();
      toast.success('Alle Aufnahmen gelöscht');
      loadRecordings();
    } catch (err) { toast.error('Fehler'); }
  };

  const formatDuration = (sec: number) => {
    const m = Math.floor(sec / 60);
    const s = sec % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  };

  const formatSize = (bytes: number) => {
    if (bytes > 1024 * 1024 * 1024) return `${(bytes / (1024**3)).toFixed(1)} GB`;
    if (bytes > 1024 * 1024) return `${(bytes / (1024**2)).toFixed(1)} MB`;
    return `${(bytes / 1024).toFixed(0)} KB`;
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Aufnahmen</h1>
          <p className="text-dark-400 mt-1">{recordings.length} Aufnahmen gespeichert</p>
        </div>
        {recordings.length > 0 && (
          <button onClick={handleDeleteAll} className="btn-danger flex items-center gap-2 text-sm">
            <Trash2 className="w-4 h-4" /> Alle löschen
          </button>
        )}
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="w-8 h-8 border-2 border-accent-blue border-t-transparent rounded-full animate-spin" />
        </div>
      ) : recordings.length === 0 ? (
        <div className="card text-center py-16">
          <Video className="w-16 h-16 text-dark-600 mx-auto mb-4" />
          <h3 className="text-lg font-medium text-white mb-2">Keine Aufnahmen</h3>
          <p className="text-dark-400">Aufnahmen werden automatisch bei Bewegung erstellt</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {recordings.map((rec) => (
            <div key={rec.id} className="card-hover">
              <div
                className="relative aspect-video bg-dark-900 rounded-lg mb-3 overflow-hidden cursor-pointer"
                onClick={() => setSelectedRecording(rec)}
              >
                <div className="absolute inset-0 flex items-center justify-center">
                  <Play className="w-12 h-12 text-dark-600" />
                </div>
                <div className="absolute bottom-2 right-2 bg-dark-900/80 px-2 py-0.5 rounded text-xs text-white">
                  {formatDuration(rec.duration)}
                </div>
              </div>
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm font-medium text-white">{rec.camera_name || 'Unbekannt'}</p>
                  <p className="text-xs text-dark-400 flex items-center gap-1 mt-1">
                    <Calendar className="w-3 h-3" />
                    {new Date(rec.created_at).toLocaleString('de-DE')}
                  </p>
                </div>
                <button
                  onClick={() => handleDelete(rec.id)}
                  className="p-2 rounded-lg text-dark-400 hover:bg-red-500/20 hover:text-red-400 transition-colors"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Player Modal */}
      {selectedRecording && (
        <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-50 p-4" onClick={() => setSelectedRecording(null)}>
          <div className="w-full max-w-4xl" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-white font-medium">{selectedRecording.camera_name}</h3>
              <button onClick={() => setSelectedRecording(null)} className="text-dark-400 hover:text-white">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="aspect-video bg-dark-900 rounded-xl overflow-hidden flex items-center justify-center">
              <div className="text-center">
                <Play className="w-16 h-16 text-dark-600 mx-auto mb-2" />
                <p className="text-dark-400">Video-Player</p>
                <p className="text-dark-500 text-sm mt-1">{selectedRecording.filename}</p>
              </div>
            </div>
            <div className="flex items-center justify-between mt-3 text-sm text-dark-400">
              <span>{formatDuration(selectedRecording.duration)}</span>
              <span>{formatSize(selectedRecording.file_size)}</span>
              <span>{new Date(selectedRecording.created_at).toLocaleString('de-DE')}</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

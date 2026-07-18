import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { camerasAPI } from '../services/api';
import type { Camera } from '../types';
import {
  Camera as CameraIcon, Plus, Battery, Wifi, WifiOff,
  Trash2, Power, MapPin, X, Eye,
} from 'lucide-react';
import toast from 'react-hot-toast';

export default function CamerasPage() {
  const [cameras, setCameras] = useState<Camera[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [newCamera, setNewCamera] = useState({ name: '', location: '' });

  useEffect(() => { loadCameras(); }, []);

  const loadCameras = async () => {
    try {
      const res = await camerasAPI.list();
      setCameras(res.data);
    } catch (err) { toast.error('Fehler beim Laden'); }
    setLoading(false);
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await camerasAPI.create(newCamera);
      toast.success('Kamera hinzugefügt');
      setShowModal(false);
      setNewCamera({ name: '', location: '' });
      loadCameras();
    } catch (err: any) {
      toast.error(err.response?.data?.detail || 'Fehler');
    }
  };

  const handleToggle = async (id: string) => {
    try {
      const res = await camerasAPI.toggle(id);
      setCameras((prev) => prev.map((c) => c.id === id ? res.data : c));
      toast.success('Status aktualisiert');
    } catch (err) { toast.error('Fehler'); }
  };

  const handleDelete = async (id: string, name: string) => {
    if (!confirm(`Kamera "${name}" wirklich löschen?`)) return;
    try {
      await camerasAPI.delete(id);
      toast.success('Kamera gelöscht');
      loadCameras();
    } catch (err) { toast.error('Fehler'); }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Kameras</h1>
          <p className="text-dark-400 mt-1">{cameras.length} Kameras registriert</p>
        </div>
        <button onClick={() => setShowModal(true)} className="btn-primary flex items-center gap-2">
          <Plus className="w-4 h-4" /> Kamera hinzufügen
        </button>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="w-8 h-8 border-2 border-accent-blue border-t-transparent rounded-full animate-spin" />
        </div>
      ) : cameras.length === 0 ? (
        <div className="card text-center py-16">
          <CameraIcon className="w-16 h-16 text-dark-600 mx-auto mb-4" />
          <h3 className="text-lg font-medium text-white mb-2">Keine Kameras</h3>
          <p className="text-dark-400 mb-4">Füge deine erste Überwachungskamera hinzu</p>
          <button onClick={() => setShowModal(true)} className="btn-primary">
            <Plus className="w-4 h-4 inline mr-2" /> Kamera hinzufügen
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {cameras.map((camera) => (
            <div key={camera.id} className="card-hover">
              {/* Preview */}
              <div className="relative aspect-video bg-dark-900 rounded-lg mb-4 overflow-hidden">
                <div className="absolute inset-0 flex items-center justify-center">
                  <CameraIcon className="w-16 h-16 text-dark-700" />
                </div>
                <div className="absolute top-2 left-2">
                  <span className={`badge ${camera.status === 'online' || camera.status === 'streaming' ? 'badge-green' : 'badge-red'}`}>
                    {camera.status === 'online' || camera.status === 'streaming' ? '● Online' : '● Offline'}
                  </span>
                </div>
              </div>

              {/* Info */}
              <div className="space-y-3">
                <div>
                  <h3 className="font-semibold text-white">{camera.name}</h3>
                  {camera.location && (
                    <p className="text-xs text-dark-400 flex items-center gap-1 mt-1">
                      <MapPin className="w-3 h-3" /> {camera.location}
                    </p>
                  )}
                </div>

                {/* Status indicators */}
                <div className="flex items-center gap-4 text-sm">
                  <div className="flex items-center gap-1">
                    <Battery className={`w-4 h-4 ${camera.battery > 50 ? 'text-green-400' : camera.battery > 20 ? 'text-amber-400' : 'text-red-400'}`} />
                    <span className="text-dark-300">{camera.battery}%</span>
                  </div>
                  <div className="flex items-center gap-1">
                    {camera.wifi_signal > 50 ? <Wifi className="w-4 h-4 text-green-400" /> : <WifiOff className="w-4 h-4 text-red-400" />}
                    <span className="text-dark-300">{camera.wifi_signal}%</span>
                  </div>
                </div>

                {/* Actions */}
                <div className="flex items-center gap-2 pt-2 border-t border-dark-600">
                  <Link to={`/cameras/${camera.id}`} className="btn-primary flex-1 flex items-center justify-center gap-2 text-sm py-2">
                    <Eye className="w-4 h-4" /> Ansehen
                  </Link>
                  <button
                    onClick={() => handleToggle(camera.id)}
                    className={`p-2 rounded-lg transition-colors ${
                      camera.is_active ? 'bg-green-500/20 text-green-400 hover:bg-green-500/30' : 'bg-dark-600 text-dark-400 hover:bg-dark-500'
                    }`}
                  >
                    <Power className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => handleDelete(camera.id, camera.name)}
                    className="p-2 rounded-lg bg-dark-600 text-dark-400 hover:bg-red-500/20 hover:text-red-400 transition-colors"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
          <div className="card w-full max-w-md" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-lg font-semibold text-white">Neue Kamera</h2>
              <button onClick={() => setShowModal(false)} className="text-dark-400 hover:text-white">
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleCreate} className="space-y-4">
              <div>
                <label className="block text-sm text-dark-300 mb-1.5">Name</label>
                <input
                  type="text"
                  value={newCamera.name}
                  onChange={(e) => setNewCamera({ ...newCamera, name: e.target.value })}
                  className="input-field w-full"
                  placeholder="z.B. Eingangstür"
                  required
                />
              </div>
              <div>
                <label className="block text-sm text-dark-300 mb-1.5">Standort (optional)</label>
                <input
                  type="text"
                  value={newCamera.location}
                  onChange={(e) => setNewCamera({ ...newCamera, location: e.target.value })}
                  className="input-field w-full"
                  placeholder="z.B. Wohnzimmer"
                />
              </div>
              <div className="flex gap-3 pt-2">
                <button type="button" onClick={() => setShowModal(false)} className="btn-secondary flex-1">Abbrechen</button>
                <button type="submit" className="btn-primary flex-1">Hinzufügen</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

import { useState, useRef, useEffect, useCallback } from 'react';
import { zonesAPI } from '../services/v1api';
import { Plus, Trash2, X, Save, Move } from 'lucide-react';
import toast from 'react-hot-toast';

interface Zone {
  id: string;
  name: string;
  camera_id: string;
  points: number[][];
  sensitivity: number;
  target_classes: string[];
  active: boolean;
  cooldown_seconds: number;
}

interface ZoneEditorProps {
  cameraId: string;
  onUpdate?: () => void;
}

export default function ZoneEditor({ cameraId, onUpdate }: ZoneEditorProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [zones, setZones] = useState<Zone[]>([]);
  const [drawing, setDrawing] = useState(false);
  const [currentPoints, setCurrentPoints] = useState<number[][]>([]);
  const [showForm, setShowForm] = useState(false);
  const [newZone, setNewZone] = useState({ name: '', sensitivity: 0.5, target_classes: ['person', 'vehicle', 'animal'], cooldown_seconds: 30 });

  useEffect(() => { loadZones(); }, [cameraId]);

  const loadZones = async () => {
    try {
      const res = await zonesAPI.getCameraZones(cameraId);
      setZones(res.data);
      drawZones();
    } catch (err) { console.error(err); }
  };

  const drawZones = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const colors = ['#3b82f6', '#22c55e', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4'];

    zones.forEach((zone, i) => {
      if (!zone.active || zone.points.length < 3) return;
      const color = colors[i % colors.length];

      ctx.beginPath();
      ctx.moveTo(zone.points[0][0], zone.points[0][1]);
      for (let j = 1; j < zone.points.length; j++) {
        ctx.lineTo(zone.points[j][0], zone.points[j][1]);
      }
      ctx.closePath();

      ctx.fillStyle = color + '20';
      ctx.fill();
      ctx.strokeStyle = color;
      ctx.lineWidth = 2;
      ctx.stroke();

      ctx.fillStyle = color;
      ctx.font = '12px sans-serif';
      ctx.fillText(zone.name, zone.points[0][0] + 5, zone.points[0][1] - 5);
    });

    // Draw current drawing
    if (currentPoints.length > 0) {
      ctx.beginPath();
      ctx.moveTo(currentPoints[0][0], currentPoints[0][1]);
      for (let j = 1; j < currentPoints.length; j++) {
        ctx.lineTo(currentPoints[j][0], currentPoints[j][1]);
      }
      ctx.strokeStyle = '#ffffff';
      ctx.lineWidth = 2;
      ctx.setLineDash([5, 5]);
      ctx.stroke();
      ctx.setLineDash([]);

      currentPoints.forEach(([x, y]) => {
        ctx.beginPath();
        ctx.arc(x, y, 4, 0, Math.PI * 2);
        ctx.fillStyle = '#ffffff';
        ctx.fill();
      });
    }
  }, [zones, currentPoints]);

  useEffect(() => { drawZones(); }, [drawZones]);

  const handleCanvasClick = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (!drawing) return;
    const canvas = canvasRef.current!;
    const rect = canvas.getBoundingClientRect();
    const x = Math.round(((e.clientX - rect.left) / rect.width) * canvas.width);
    const y = Math.round(((e.clientY - rect.top) / rect.height) * canvas.height);
    setCurrentPoints([...currentPoints, [x, y]]);
  };

  const handleFinishZone = () => {
    if (currentPoints.length < 3) {
      toast.error('Mindestens 3 Punkte erforderlich');
      return;
    }
    setShowForm(true);
  };

  const handleSaveZone = async () => {
    try {
      await zonesAPI.createZone(cameraId, {
        name: newZone.name,
        points: JSON.stringify(currentPoints),
        sensitivity: newZone.sensitivity,
        target_classes: newZone.target_classes,
        cooldown_seconds: newZone.cooldown_seconds,
      });
      toast.success('Zone erstellt');
      setCurrentPoints([]);
      setDrawing(false);
      setShowForm(false);
      setNewZone({ name: '', sensitivity: 0.5, target_classes: ['person', 'vehicle', 'animal'], cooldown_seconds: 30 });
      loadZones();
      onUpdate?.();
    } catch (err) {
      toast.error('Fehler beim Erstellen');
    }
  };

  const handleDeleteZone = async (zoneId: string) => {
    if (!confirm('Zone wirklich löschen?')) return;
    try {
      await zonesAPI.deleteZone(zoneId);
      toast.success('Zone gelöscht');
      loadZones();
      onUpdate?.();
    } catch (err) {
      toast.error('Fehler');
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-white">Erkennungszonen</h3>
        <div className="flex gap-2">
          {drawing ? (
            <>
              <button onClick={handleFinishZone} className="btn-primary text-xs py-1 px-2">
                <Save className="w-3 h-3 inline mr-1" /> Fertig
              </button>
              <button onClick={() => { setDrawing(false); setCurrentPoints([]); }} className="btn-secondary text-xs py-1 px-2">
                <X className="w-3 h-3 inline mr-1" /> Abbrechen
              </button>
            </>
          ) : (
            <button onClick={() => setDrawing(true)} className="btn-secondary text-xs py-1 px-2">
              <Plus className="w-3 h-3 inline mr-1" /> Zone zeichnen
            </button>
          )}
        </div>
      </div>

      {drawing && (
        <p className="text-xs text-dark-400">Klicke auf die Kamera-Ansicht um Punkte zu setzen (min. 3)</p>
      )}

      <canvas
        ref={canvasRef}
        width={640}
        height={360}
        onClick={handleCanvasClick}
        className={`w-full rounded-lg border ${drawing ? 'border-accent-blue cursor-crosshair' : 'border-dark-600'} bg-dark-900`}
      />

      {/* Zone list */}
      <div className="space-y-1.5">
        {zones.map((zone) => (
          <div key={zone.id} className="flex items-center justify-between bg-dark-800 rounded-lg px-3 py-2 text-sm">
            <div>
              <span className="text-white">{zone.name}</span>
              <span className="text-dark-400 ml-2 text-xs">
                {zone.target_classes.join(', ')} · {zone.sensitivity.toFixed(0)}%
              </span>
            </div>
            <button onClick={() => handleDeleteZone(zone.id)} className="text-dark-400 hover:text-red-400">
              <Trash2 className="w-3.5 h-3.5" />
            </button>
          </div>
        ))}
      </div>

      {/* Save form modal */}
      {showForm && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
          <div className="card w-full max-w-md" onClick={(e) => e.stopPropagation()}>
            <h3 className="text-lg font-semibold text-white mb-4">Neue Zone</h3>
            <div className="space-y-3">
              <input
                type="text"
                value={newZone.name}
                onChange={(e) => setNewZone({ ...newZone, name: e.target.value })}
                className="input-field w-full"
                placeholder="Zonenname"
              />
              <div>
                <label className="text-xs text-dark-400">Empfindlichkeit: {Math.round(newZone.sensitivity * 100)}%</label>
                <input
                  type="range" min="0" max="100"
                  value={newZone.sensitivity * 100}
                  onChange={(e) => setNewZone({ ...newZone, sensitivity: Number(e.target.value) / 100 })}
                  className="w-full"
                />
              </div>
              <div className="flex gap-2">
                {['person', 'vehicle', 'animal'].map((cls) => (
                  <label key={cls} className="flex items-center gap-1 text-xs text-dark-300">
                    <input
                      type="checkbox"
                      checked={newZone.target_classes.includes(cls)}
                      onChange={(e) => {
                        const classes = e.target.checked
                          ? [...newZone.target_classes, cls]
                          : newZone.target_classes.filter((c) => c !== cls);
                        setNewZone({ ...newZone, target_classes: classes });
                      }}
                      className="accent-blue-500"
                    />
                    {cls}
                  </label>
                ))}
              </div>
              <div className="flex gap-2 pt-2">
                <button onClick={() => setShowForm(false)} className="btn-secondary flex-1">Abbrechen</button>
                <button onClick={handleSaveZone} className="btn-primary flex-1">Speichern</button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

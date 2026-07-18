import { useState, useEffect } from 'react';
import { aiAPI, recordingAPI, auditAPI, sessionsAPI } from '../services/v1api';
import {
  Brain, HardDrive, Activity, Shield, Clock, RefreshCw,
  Download, CheckCircle, XCircle, AlertTriangle, Eye, Settings,
} from 'lucide-react';
import toast from 'react-hot-toast';

export default function AISettingsPage() {
  const [modelStatus, setModelStatus] = useState<any>(null);
  const [detectorConfig, setDetectorConfig] = useState<any>(null);
  const [storageInfo, setStorageInfo] = useState<any>(null);
  const [sessions, setSessions] = useState<any[]>([]);
  const [auditLogs, setAuditLogs] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => { loadAll(); }, []);

  const loadAll = async () => {
    try {
      const [model, config, storage, sess, logs] = await Promise.all([
        aiAPI.getModelStatus().catch(() => ({ data: {} })),
        aiAPI.getDetectorConfig().catch(() => ({ data: {} })),
        recordingAPI.getStorage().catch(() => ({ data: {} })),
        sessionsAPI.getMySessions().catch(() => ({ data: [] })),
        auditAPI.getLogs({ limit: 20 }).catch(() => ({ data: [] })),
      ]);
      setModelStatus(model.data);
      setDetectorConfig(config.data);
      setStorageInfo(storage.data);
      setSessions(sess.data);
      setAuditLogs(logs.data);
    } catch {}
    setLoading(false);
  };

  const handleDownloadModel = async () => {
    toast.loading('Downloading YOLO model...');
    try {
      await aiAPI.downloadModel();
      toast.success('Download started');
      loadAll();
    } catch { toast.error('Download failed'); }
  };

  const handleLoadModel = async () => {
    try {
      await aiAPI.loadModel();
      toast.success('Model loaded');
      loadAll();
    } catch { toast.error('Failed to load model'); }
  };

  const handleCleanup = async () => {
    if (!confirm('Alte Aufnahmen wirklich löschen?')) return;
    try {
      const res = await recordingAPI.cleanup();
      toast.success(`${res.data.deleted} Dateien gelöscht`);
      loadAll();
    } catch { toast.error('Cleanup failed'); }
  };

  const handleRevokeAll = async () => {
    if (!confirm('Alle Sessions wirklich beenden?')) return;
    try {
      await sessionsAPI.revokeAll();
      toast.success('Alle Sessions beendet');
      loadAll();
    } catch { toast.error('Fehler'); }
  };

  if (loading) {
    return <div className="flex items-center justify-center h-64">
      <div className="w-8 h-8 border-2 border-accent-blue border-t-transparent rounded-full animate-spin" />
    </div>;
  }

  return (
    <div className="space-y-6 max-w-4xl">
      <h1 className="text-2xl font-bold text-white">KI & Erweiterte Einstellungen</h1>

      {/* YOLO Model */}
      <div className="card">
        <div className="flex items-center gap-3 mb-4">
          <Brain className="w-5 h-5 text-accent-purple" />
          <h2 className="text-lg font-semibold text-white">YOLO Erkennungsmodell</h2>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
          <div className="bg-dark-800 rounded-lg p-3 text-center">
            <p className="text-xs text-dark-400">Status</p>
            <p className={`font-medium ${modelStatus?.loaded ? 'text-green-400' : 'text-red-400'}`}>
              {modelStatus?.loaded ? 'Geladen' : 'Nicht geladen'}
            </p>
          </div>
          <div className="bg-dark-800 rounded-lg p-3 text-center">
            <p className="text-xs text-dark-400">Modelle vorhanden</p>
            <p className={`font-medium ${modelStatus?.models_exist ? 'text-green-400' : 'text-amber-400'}`}>
              {modelStatus?.models_exist ? 'Ja' : 'Nein'}
            </p>
          </div>
          <div className="bg-dark-800 rounded-lg p-3 text-center">
            <p className="text-xs text-dark-400">Output Layers</p>
            <p className="font-medium text-white">{modelStatus?.output_layers || 0}</p>
          </div>
        </div>
        <div className="flex gap-2">
          {!modelStatus?.models_exist && (
            <button onClick={handleDownloadModel} className="btn-primary text-sm flex items-center gap-1">
              <Download className="w-4 h-4" /> Model herunterladen
            </button>
          )}
          {modelStatus?.models_exist && !modelStatus?.loaded && (
            <button onClick={handleLoadModel} className="btn-primary text-sm flex items-center gap-1">
              <CheckCircle className="w-4 h-4" /> Model laden
            </button>
          )}
        </div>
      </div>

      {/* Detector Config */}
      <div className="card">
        <div className="flex items-center gap-3 mb-4">
          <Eye className="w-5 h-5 text-accent-cyan" />
          <h2 className="text-lg font-semibold text-white">Erkennungskonfiguration</h2>
        </div>
        <div className="space-y-3">
          <div className="flex items-center justify-between bg-dark-800 rounded-lg p-3">
            <span className="text-dark-300">Mindestvertrauen</span>
            <span className="text-white font-medium">{Math.round((detectorConfig?.min_confidence || 0.4) * 100)}%</span>
          </div>
          <div className="flex items-center justify-between bg-dark-800 rounded-lg p-3">
            <span className="text-dark-300">Aktive Klassen</span>
            <span className="text-white font-medium">{detectorConfig?.target_classes?.join(', ') || 'Alle'}</span>
          </div>
          <div className="flex items-center justify-between bg-dark-800 rounded-lg p-3">
            <span className="text-dark-300">Erkennung aktiv</span>
            <span className={detectorConfig?.active ? 'text-green-400' : 'text-red-400'}>
              {detectorConfig?.active ? 'Ja' : 'Nein'}
            </span>
          </div>
        </div>
      </div>

      {/* Storage */}
      <div className="card">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-3">
            <HardDrive className="w-5 h-5 text-accent-green" />
            <h2 className="text-lg font-semibold text-white">Speicher</h2>
          </div>
          <button onClick={handleCleanup} className="btn-secondary text-sm flex items-center gap-1">
            <RefreshCw className="w-3.5 h-3.5" /> Aufräumen
          </button>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div className="bg-dark-800 rounded-lg p-3 text-center">
            <p className="text-2xl font-bold text-white">{storageInfo?.total_size || '0 KB'}</p>
            <p className="text-xs text-dark-400">Belegt</p>
          </div>
          <div className="bg-dark-800 rounded-lg p-3 text-center">
            <p className="text-2xl font-bold text-white">{storageInfo?.file_count || 0}</p>
            <p className="text-xs text-dark-400">Dateien</p>
          </div>
        </div>
      </div>

      {/* Sessions */}
      <div className="card">
        <div className="flex items-center justify-between mb-4">
          <div className="flex items-center gap-3">
            <Shield className="w-5 h-5 text-accent-amber" />
            <h2 className="text-lg font-semibold text-white">Aktive Sessions</h2>
          </div>
          <button onClick={handleRevokeAll} className="btn-danger text-sm">
            Alle beenden
          </button>
        </div>
        {sessions.length === 0 ? (
          <p className="text-dark-400 text-sm">Keine aktiven Sessions</p>
        ) : (
          <div className="space-y-2">
            {sessions.map((s) => (
              <div key={s.session_id} className="bg-dark-800 rounded-lg p-3 text-sm">
                <div className="flex justify-between">
                  <span className="text-dark-300">{s.ip_address}</span>
                  <span className="text-dark-500 text-xs">{new Date(s.last_active).toLocaleString('de-DE')}</span>
                </div>
                <p className="text-xs text-dark-500 mt-1 truncate">{s.user_agent}</p>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Audit Logs */}
      <div className="card">
        <div className="flex items-center gap-3 mb-4">
          <Clock className="w-5 h-5 text-accent-blue" />
          <h2 className="text-lg font-semibold text-white">Audit Logs</h2>
        </div>
        {auditLogs.length === 0 ? (
          <p className="text-dark-400 text-sm">Keine Logs vorhanden</p>
        ) : (
          <div className="space-y-1.5 max-h-64 overflow-y-auto">
            {auditLogs.map((log, i) => (
              <div key={i} className="bg-dark-800 rounded px-3 py-2 text-xs flex items-center gap-3">
                <span className={`badge ${log.status_code < 400 ? 'badge-green' : 'badge-red'}`}>{log.status_code}</span>
                <span className="text-white">{log.action}</span>
                <span className="text-dark-400">{log.method} {log.path}</span>
                <span className="text-dark-500 ml-auto">{new Date(log.timestamp).toLocaleTimeString('de-DE')}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

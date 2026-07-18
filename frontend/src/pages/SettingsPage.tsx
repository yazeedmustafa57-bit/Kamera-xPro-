import { useState } from 'react';
import { useAuth } from '../hooks/useAuth';
import {
  Settings, Shield, Bell, Eye, HardDrive, Save,
} from 'lucide-react';
import toast from 'react-hot-toast';

export default function SettingsPage() {
  const { user } = useAuth();
  const [sensitivity, setSensitivity] = useState(50);
  const [emailAlerts, setEmailAlerts] = useState(true);
  const [pushAlerts, setPushAlerts] = useState(true);
  const [motionTypes, setMotionTypes] = useState({ person: true, car: true, animal: false, motion: true });
  const [recordingRetention, setRecordingRetention] = useState(30);
  const [autoRecord, setAutoRecord] = useState(true);

  const handleSave = () => {
    toast.success('Einstellungen gespeichert');
  };

  return (
    <div className="space-y-6 max-w-3xl">
      <div>
        <h1 className="text-2xl font-bold text-white">Einstellungen</h1>
        <p className="text-dark-400 mt-1">Systemkonfiguration anpassen</p>
      </div>

      {/* Motion Detection */}
      <div className="card">
        <div className="flex items-center gap-3 mb-5">
          <Eye className="w-5 h-5 text-accent-blue" />
          <h2 className="text-lg font-semibold text-white">Bewegungserkennung</h2>
        </div>

        <div className="space-y-5">
          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="text-sm text-dark-300">Empfindlichkeit</label>
              <span className="text-sm font-medium text-accent-blue">{sensitivity}%</span>
            </div>
            <input
              type="range"
              min="0"
              max="100"
              value={sensitivity}
              onChange={(e) => setSensitivity(Number(e.target.value))}
              className="w-full h-2 bg-dark-600 rounded-full appearance-none cursor-pointer accent-blue-500"
            />
            <div className="flex justify-between text-xs text-dark-500 mt-1">
              <span>Niedrig</span>
              <span>Hoch</span>
            </div>
          </div>

          <div>
            <label className="text-sm text-dark-300 mb-3 block">Erkennungsarten</label>
            <div className="grid grid-cols-2 gap-3">
              {[
                { key: 'person', label: 'Personen' },
                { key: 'car', label: 'Fahrzeuge' },
                { key: 'animal', label: 'Tiere' },
                { key: 'motion', label: 'Allgemeine Bewegung' },
              ].map((type) => (
                <label
                  key={type.key}
                  className={`flex items-center gap-3 p-3 rounded-lg border cursor-pointer transition-all ${
                    motionTypes[type.key as keyof typeof motionTypes]
                      ? 'border-accent-blue/50 bg-accent-blue/10'
                      : 'border-dark-600 bg-dark-800 hover:border-dark-500'
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={motionTypes[type.key as keyof typeof motionTypes]}
                    onChange={(e) => setMotionTypes({ ...motionTypes, [type.key]: e.target.checked })}
                    className="w-4 h-4 accent-blue-500"
                  />
                  <span className="text-sm text-dark-200">{type.label}</span>
                </label>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Notifications */}
      <div className="card">
        <div className="flex items-center gap-3 mb-5">
          <Bell className="w-5 h-5 text-accent-amber" />
          <h2 className="text-lg font-semibold text-white">Benachrichtigungen</h2>
        </div>

        <div className="space-y-4">
          {[
            { label: 'E-Mail Benachrichtigungen', desc: 'Alarme per E-Mail erhalten', checked: emailAlerts, onChange: setEmailAlerts },
            { label: 'Push Benachrichtigungen', desc: 'Push-Nachrichten im Browser', checked: pushAlerts, onChange: setPushAlerts },
          ].map((item) => (
            <div key={item.label} className="flex items-center justify-between p-3 bg-dark-800 rounded-lg">
              <div>
                <p className="text-sm font-medium text-white">{item.label}</p>
                <p className="text-xs text-dark-400 mt-0.5">{item.desc}</p>
              </div>
              <label className="relative inline-flex items-center cursor-pointer">
                <input
                  type="checkbox"
                  checked={item.checked}
                  onChange={(e) => item.onChange(e.target.checked)}
                  className="sr-only peer"
                />
                <div className="w-11 h-6 bg-dark-600 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-dark-300 after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-accent-blue peer-checked:after:bg-white" />
              </label>
            </div>
          ))}
        </div>
      </div>

      {/* Recording */}
      <div className="card">
        <div className="flex items-center gap-3 mb-5">
          <HardDrive className="w-5 h-5 text-accent-green" />
          <h2 className="text-lg font-semibold text-white">Aufnahmen</h2>
        </div>

        <div className="space-y-4">
          <div className="flex items-center justify-between p-3 bg-dark-800 rounded-lg">
            <div>
              <p className="text-sm font-medium text-white">Automatische Aufnahme</p>
              <p className="text-xs text-dark-400 mt-0.5">Bei erkannter Bewegung aufnehmen</p>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input type="checkbox" checked={autoRecord} onChange={(e) => setAutoRecord(e.target.checked)} className="sr-only peer" />
              <div className="w-11 h-6 bg-dark-600 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-dark-300 after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-accent-blue peer-checked:after:bg-white" />
            </label>
          </div>

          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="text-sm text-dark-300">Aufbewahrung</label>
              <span className="text-sm font-medium text-accent-blue">{recordingRetention} Tage</span>
            </div>
            <input
              type="range"
              min="1"
              max="90"
              value={recordingRetention}
              onChange={(e) => setRecordingRetention(Number(e.target.value))}
              className="w-full h-2 bg-dark-600 rounded-full appearance-none cursor-pointer accent-blue-500"
            />
          </div>
        </div>
      </div>

      {/* Account */}
      <div className="card">
        <div className="flex items-center gap-3 mb-5">
          <Shield className="w-5 h-5 text-accent-purple" />
          <h2 className="text-lg font-semibold text-white">Konto</h2>
        </div>

        <div className="space-y-3 text-sm">
          <div className="flex items-center justify-between p-3 bg-dark-800 rounded-lg">
            <span className="text-dark-300">Benutzername</span>
            <span className="text-white font-medium">{user?.username}</span>
          </div>
          <div className="flex items-center justify-between p-3 bg-dark-800 rounded-lg">
            <span className="text-dark-300">E-Mail</span>
            <span className="text-white font-medium">{user?.email}</span>
          </div>
          <div className="flex items-center justify-between p-3 bg-dark-800 rounded-lg">
            <span className="text-dark-300">Rolle</span>
            <span className={`badge ${user?.role === 'admin' ? 'badge-blue' : 'badge-green'}`}>
              {user?.role === 'admin' ? 'Administrator' : 'Benutzer'}
            </span>
          </div>
        </div>
      </div>

      {/* Save */}
      <div className="flex justify-end">
        <button onClick={handleSave} className="btn-primary flex items-center gap-2">
          <Save className="w-4 h-4" /> Einstellungen speichern
        </button>
      </div>
    </div>
  );
}

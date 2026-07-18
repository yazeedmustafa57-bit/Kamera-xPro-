import { useState, useEffect } from 'react';
import { authAPI } from '../services/api';
import { useAuth } from '../hooks/useAuth';
import type { User } from '../types';
import { Users, Trash2, Shield, User as UserIcon, Plus, X } from 'lucide-react';
import toast from 'react-hot-toast';

export default function UsersPage() {
  const { user: currentUser } = useAuth();
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [newUser, setNewUser] = useState({ username: '', email: '', password: '', role: 'user' });

  useEffect(() => { loadUsers(); }, []);

  const loadUsers = async () => {
    try {
      const res = await authAPI.getUsers();
      setUsers(res.data);
    } catch (err) { toast.error('Fehler'); }
    setLoading(false);
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await authAPI.register(newUser);
      toast.success('Benutzer erstellt');
      setShowModal(false);
      setNewUser({ username: '', email: '', password: '', role: 'user' });
      loadUsers();
    } catch (err: any) {
      toast.error(err.response?.data?.detail || 'Fehler');
    }
  };

  const handleDelete = async (id: string, name: string) => {
    if (!confirm(`Benutzer "${name}" wirklich löschen?`)) return;
    try {
      await authAPI.deleteUser(id);
      toast.success('Benutzer gelöscht');
      loadUsers();
    } catch (err: any) {
      toast.error(err.response?.data?.detail || 'Fehler');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white">Benutzer</h1>
          <p className="text-dark-400 mt-1">{users.length} registrierte Benutzer</p>
        </div>
        {currentUser?.role === 'admin' && (
          <button onClick={() => setShowModal(true)} className="btn-primary flex items-center gap-2">
            <Plus className="w-4 h-4" /> Benutzer hinzufügen
          </button>
        )}
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64">
          <div className="w-8 h-8 border-2 border-accent-blue border-t-transparent rounded-full animate-spin" />
        </div>
      ) : (
        <div className="space-y-3">
          {users.map((u) => (
            <div key={u.id} className="card flex items-center gap-4">
              <div className={`w-12 h-12 rounded-full flex items-center justify-center ${
                u.role === 'admin' ? 'bg-accent-purple/20' : 'bg-accent-blue/20'
              }`}>
                {u.role === 'admin' ? (
                  <Shield className="w-6 h-6 text-accent-purple" />
                ) : (
                  <UserIcon className="w-6 h-6 text-accent-blue" />
                )}
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <p className="font-medium text-white">{u.username}</p>
                  <span className={`badge ${u.role === 'admin' ? 'badge-blue' : 'badge-green'}`}>
                    {u.role === 'admin' ? 'Admin' : 'Benutzer'}
                  </span>
                  {u.id === currentUser?.id && (
                    <span className="badge badge-amber">Du</span>
                  )}
                </div>
                <p className="text-sm text-dark-400 mt-0.5">{u.email}</p>
              </div>
              <div className="text-right">
                <p className="text-xs text-dark-500">
                  Registriert: {new Date(u.created_at).toLocaleDateString('de-DE')}
                </p>
              </div>
              {currentUser?.role === 'admin' && u.id !== currentUser?.id && (
                <button
                  onClick={() => handleDelete(u.id, u.username)}
                  className="p-2 rounded-lg text-dark-400 hover:bg-red-500/20 hover:text-red-400 transition-colors"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4">
          <div className="card w-full max-w-md" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-lg font-semibold text-white">Neuer Benutzer</h2>
              <button onClick={() => setShowModal(false)} className="text-dark-400 hover:text-white">
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleCreate} className="space-y-4">
              <div>
                <label className="block text-sm text-dark-300 mb-1.5">Benutzername</label>
                <input type="text" value={newUser.username} onChange={(e) => setNewUser({ ...newUser, username: e.target.value })} className="input-field w-full" required />
              </div>
              <div>
                <label className="block text-sm text-dark-300 mb-1.5">E-Mail</label>
                <input type="email" value={newUser.email} onChange={(e) => setNewUser({ ...newUser, email: e.target.value })} className="input-field w-full" required />
              </div>
              <div>
                <label className="block text-sm text-dark-300 mb-1.5">Passwort</label>
                <input type="password" value={newUser.password} onChange={(e) => setNewUser({ ...newUser, password: e.target.value })} className="input-field w-full" required />
              </div>
              <div>
                <label className="block text-sm text-dark-300 mb-1.5">Rolle</label>
                <select value={newUser.role} onChange={(e) => setNewUser({ ...newUser, role: e.target.value })} className="input-field w-full">
                  <option value="user">Benutzer</option>
                  <option value="admin">Administrator</option>
                </select>
              </div>
              <div className="flex gap-3 pt-2">
                <button type="button" onClick={() => setShowModal(false)} className="btn-secondary flex-1">Abbrechen</button>
                <button type="submit" className="btn-primary flex-1">Erstellen</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}

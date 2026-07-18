import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { Shield, Eye, EyeOff, LogIn, UserPlus } from 'lucide-react';
import toast from 'react-hot-toast';

export default function LoginPage() {
  const [isRegister, setIsRegister] = useState(false);
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const { login, register } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      if (isRegister) {
        await register(username, email, password);
        toast.success('Konto erstellt!');
      } else {
        await login(username, password);
        toast.success('Willkommen zurück!');
      }
      navigate('/');
    } catch (err: any) {
      toast.error(err.response?.data?.detail || 'Fehler bei der Anmeldung');
    }
    setLoading(false);
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-dark-900 p-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="w-16 h-16 bg-gradient-to-br from-accent-blue to-accent-cyan rounded-2xl flex items-center justify-center mx-auto mb-4 shadow-lg shadow-accent-blue/20">
            <Shield className="w-8 h-8 text-white" />
          </div>
          <h1 className="text-3xl font-bold text-white">SmartCam Pro</h1>
          <p className="text-dark-400 mt-2">Professional Security System</p>
        </div>

        {/* Form */}
        <div className="card">
          <h2 className="text-xl font-semibold text-white mb-6">
            {isRegister ? 'Konto erstellen' : 'Anmelden'}
          </h2>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm text-dark-300 mb-1.5">Benutzername</label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className="input-field w-full"
                placeholder="Benutzername eingeben"
                required
              />
            </div>

            {isRegister && (
              <div>
                <label className="block text-sm text-dark-300 mb-1.5">E-Mail</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="input-field w-full"
                  placeholder="E-Mail Adresse"
                  required
                />
              </div>
            )}

            <div>
              <label className="block text-sm text-dark-300 mb-1.5">Passwort</label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="input-field w-full pr-10"
                  placeholder="Passwort eingeben"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-dark-400 hover:text-dark-200"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="btn-primary w-full flex items-center justify-center gap-2 py-3"
            >
              {loading ? (
                <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
              ) : isRegister ? (
                <><UserPlus className="w-4 h-4" /> Konto erstellen</>
              ) : (
                <><LogIn className="w-4 h-4" /> Anmelden</>
              )}
            </button>
          </form>

          <div className="mt-6 text-center">
            <button
              onClick={() => setIsRegister(!isRegister)}
              className="text-sm text-accent-blue hover:text-blue-400 transition-colors"
            >
              {isRegister ? 'Bereits ein Konto? Anmelden' : 'Kein Konto? Registrieren'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

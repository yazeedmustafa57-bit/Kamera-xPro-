import { Outlet, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import {
  LayoutDashboard, Camera, Video, Bell, Users, Settings, LogOut,
  Shield, Wifi, WifiOff, Menu, X, Brain,
} from 'lucide-react';
import { useState } from 'react';

const navItems = [
  { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
  { to: '/cameras', icon: Camera, label: 'Kameras' },
  { to: '/recordings', icon: Video, label: 'Aufnahmen' },
  { to: '/alarms', icon: Bell, label: 'Alarme' },
  { to: '/users', icon: Users, label: 'Benutzer' },
  { to: '/ai-settings', icon: Brain, label: 'KI & Erweitert' },
  { to: '/settings', icon: Settings, label: 'Einstellungen' },
];

export default function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const handleLogout = () => { logout(); navigate('/login'); };

  return (
    <div className="flex h-screen overflow-hidden">
      {sidebarOpen && (
        <div className="fixed inset-0 bg-black/50 z-40 lg:hidden" onClick={() => setSidebarOpen(false)} />
      )}

      <aside className={`
        fixed lg:static inset-y-0 left-0 z-50 w-64 bg-dark-900 border-r border-dark-600
        transform transition-transform duration-300 ease-in-out
        ${sidebarOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}
        flex flex-col
      `}>
        <div className="flex items-center gap-3 px-6 py-5 border-b border-dark-600">
          <div className="w-10 h-10 bg-gradient-to-br from-accent-blue to-accent-cyan rounded-xl flex items-center justify-center">
            <Shield className="w-5 h-5 text-white" />
          </div>
          <div>
            <h1 className="text-lg font-bold text-white">SmartCam</h1>
            <p className="text-xs text-dark-400">Pro v1.1</p>
          </div>
          <button className="ml-auto lg:hidden text-dark-400 hover:text-white" onClick={() => setSidebarOpen(false)}>
            <X className="w-5 h-5" />
          </button>
        </div>

        <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              onClick={() => setSidebarOpen(false)}
              className={({ isActive }) =>
                `sidebar-link ${isActive ? 'active' : ''}`
              }
            >
              <item.icon className="w-5 h-5" />
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="p-4 border-t border-dark-600">
          <div className="flex items-center gap-3 mb-3">
            <div className="w-9 h-9 bg-accent-purple rounded-full flex items-center justify-center text-white text-sm font-bold">
              {user?.username?.[0]?.toUpperCase() || 'U'}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium text-white truncate">{user?.username}</p>
              <p className="text-xs text-dark-400 capitalize">{user?.role}</p>
            </div>
          </div>
          <button onClick={handleLogout} className="sidebar-link w-full text-accent-red hover:bg-red-500/10">
            <LogOut className="w-5 h-5" />
            <span>Abmelden</span>
          </button>
        </div>
      </aside>

      <main className="flex-1 flex flex-col overflow-hidden">
        <header className="flex items-center gap-4 px-4 lg:px-6 py-3 bg-dark-800 border-b border-dark-600">
          <button className="lg:hidden text-dark-400 hover:text-white" onClick={() => setSidebarOpen(true)}>
            <Menu className="w-6 h-6" />
          </button>
          <div className="flex-1" />
          <div className="flex items-center gap-2 text-sm">
            <Wifi className="w-4 h-4 text-green-400" />
            <span className="text-dark-300">SmartCam Pro v1.1</span>
          </div>
        </header>

        <div className="flex-1 overflow-y-auto p-4 lg:p-6">
          <Outlet />
        </div>
      </main>
    </div>
  );
}

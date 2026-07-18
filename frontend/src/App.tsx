import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './hooks/useAuth';
import Layout from './components/Layout';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import CamerasPage from './pages/CamerasPage';
import CameraDetailPage from './pages/CameraDetailPage';
import RecordingsPage from './pages/RecordingsPage';
import AlarmsPage from './pages/AlarmsPage';
import UsersPage from './pages/UsersPage';
import SettingsPage from './pages/SettingsPage';
import AISettingsPage from './pages/AISettingsPage';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { isAuthenticated, loading } = useAuth();
  if (loading) {
    return (
      <div className="flex items-center justify-center h-screen bg-dark-800">
        <div className="w-10 h-10 border-2 border-accent-blue border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<ProtectedRoute><Layout /></ProtectedRoute>}>
        <Route index element={<DashboardPage />} />
        <Route path="cameras" element={<CamerasPage />} />
        <Route path="cameras/:id" element={<CameraDetailPage />} />
        <Route path="recordings" element={<RecordingsPage />} />
        <Route path="alarms" element={<AlarmsPage />} />
        <Route path="users" element={<UsersPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="ai-settings" element={<AISettingsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export interface User {
  id: string;
  username: string;
  email: string;
  role: 'admin' | 'user';
  is_active: boolean;
  created_at: string;
}

export interface Camera {
  id: string;
  user_id: string;
  name: string;
  location: string;
  status: 'online' | 'offline' | 'recording' | 'streaming';
  battery: number;
  wifi_signal: number;
  is_active: boolean;
  stream_url: string;
  created_at: string;
  updated_at?: string;
}

export interface Event {
  id: string;
  camera_id: string;
  type: string;
  confidence: string;
  description: string;
  image_path: string;
  video_path: string;
  is_read: boolean;
  created_at: string;
  camera_name?: string;
}

export interface Recording {
  id: string;
  camera_id: string;
  filename: string;
  filepath: string;
  duration: number;
  file_size: number;
  created_at: string;
  camera_name?: string;
}

export interface DashboardStats {
  total_cameras: number;
  online_cameras: number;
  total_events: number;
  events_today: number;
  total_recordings: number;
  storage_used: string;
}

export interface Token {
  access_token: string;
  token_type: string;
  user: User;
}

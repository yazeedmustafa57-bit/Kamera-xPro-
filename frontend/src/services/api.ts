import axios from 'axios';
import type { User, Camera, Event, Recording, DashboardStats, Token } from '../types';

const API_BASE = '/api';

const api = axios.create({ baseURL: API_BASE });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

// Auth
export const authAPI = {
  login: (data: { username: string; password: string }) =>
    api.post<Token>('/auth/login', data),
  register: (data: { username: string; email: string; password: string; role?: string }) =>
    api.post<Token>('/auth/register', data),
  getMe: () => api.get<User>('/auth/me'),
  getUsers: () => api.get<User[]>('/auth/users'),
  deleteUser: (id: string) => api.delete(`/auth/users/${id}`),
};

// Cameras
export const camerasAPI = {
  list: () => api.get<Camera[]>('/cameras/'),
  get: (id: string) => api.get<Camera>(`/cameras/${id}`),
  create: (data: { name: string; location?: string }) => api.post<Camera>('/cameras/', data),
  update: (id: string, data: Partial<Camera>) => api.put<Camera>(`/cameras/${id}`, data),
  delete: (id: string) => api.delete(`/cameras/${id}`),
  toggle: (id: string) => api.post<Camera>(`/cameras/${id}/toggle`),
};

// Events
export const eventsAPI = {
  list: (params?: { camera_id?: string; event_type?: string; days?: number; limit?: number }) =>
    api.get<Event[]>('/events/', { params }),
  markRead: (id: string) => api.put(`/events/${id}/read`),
  delete: (id: string) => api.delete(`/events/${id}`),
};

// Recordings
export const recordingsAPI = {
  list: (params?: { camera_id?: string; limit?: number }) =>
    api.get<Recording[]>('/recordings/', { params }),
  delete: (id: string) => api.delete(`/recordings/${id}`),
  deleteAll: () => api.delete('/recordings/'),
};

// Dashboard
export const dashboardAPI = {
  getStats: () => api.get<DashboardStats>('/dashboard/stats'),
};

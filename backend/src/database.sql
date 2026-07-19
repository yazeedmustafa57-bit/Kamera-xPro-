-- SmartCam Pro Database Schema
-- PostgreSQL

-- Nutzer-Accounts
CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(100),
  subscription_tier VARCHAR(20) DEFAULT 'free',
  created_at TIMESTAMP DEFAULT now(),
  email_verified BOOLEAN DEFAULT false
);

-- Kameras — IMMER an genau einen User gebunden
CREATE TABLE IF NOT EXISTS cameras (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(100) NOT NULL,
  device_model VARCHAR(100),
  status VARCHAR(20) DEFAULT 'offline',
  last_seen TIMESTAMP,
  pairing_code VARCHAR(10),
  created_at TIMESTAMP DEFAULT now()
);

-- Ereignisse (Bewegungserkennung, Alarme)
CREATE TABLE IF NOT EXISTS events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  camera_id UUID NOT NULL REFERENCES cameras(id) ON DELETE CASCADE,
  type VARCHAR(30) NOT NULL,
  thumbnail_url TEXT,
  video_url TEXT,
  created_at TIMESTAMP DEFAULT now()
);

-- Push-Tokens fuer Benachrichtigungen
CREATE TABLE IF NOT EXISTS push_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  fcm_token TEXT NOT NULL,
  platform VARCHAR(10) DEFAULT 'android'
);

-- Indizes fuer Performance
CREATE INDEX IF NOT EXISTS idx_cameras_owner ON cameras(owner_id);
CREATE INDEX IF NOT EXISTS idx_events_camera ON events(camera_id);
CREATE INDEX IF NOT EXISTS idx_push_tokens_user ON push_tokens(user_id);

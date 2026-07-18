import os
import uuid
import time
import asyncio
import cv2
import numpy as np
from datetime import datetime, timezone, timedelta
from typing import Optional, Dict, List
from pathlib import Path
from dataclasses import dataclass, field
import struct
import json


@dataclass
class RecordingSession:
    session_id: str
    camera_id: str
    filepath: str
    started_at: datetime
    frames: list = field(default_factory=list)
    frame_count: int = 0
    fps: float = 15.0
    resolution: tuple = (1280, 720)
    is_recording: bool = True


class RecordingEngine:
    def __init__(self, recording_dir: str = "static/recordings", max_days: int = 30):
        self.recording_dir = Path(recording_dir)
        self.recording_dir.mkdir(parents=True, exist_ok=True)
        self.max_days = max_days
        self.active_sessions: Dict[str, RecordingSession] = {}
        self._cleanup_task: Optional[asyncio.Task] = None

    async def start_recording(self, camera_id: str, fps: float = 15.0, resolution: tuple = (1280, 720)) -> str:
        session_id = str(uuid.uuid4())
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
        filename = f"{camera_id}_{timestamp}_{session_id[:8]}.avi"
        filepath = str(self.recording_dir / filename)

        session = RecordingSession(
            session_id=session_id,
            camera_id=camera_id,
            filepath=filepath,
            started_at=datetime.now(timezone.utc),
            fps=fps,
            resolution=resolution,
        )

        self.active_sessions[session_id] = session
        return session_id

    async def write_frame(self, session_id: str, frame: np.ndarray) -> bool:
        session = self.active_sessions.get(session_id)
        if not session or not session.is_recording:
            return False

        try:
            resized = cv2.resize(frame, session.resolution)
            session.frames.append(resized)
            session.frame_count += 1
            return True
        except Exception:
            return False

    async def stop_recording(self, session_id: str) -> Optional[dict]:
        session = self.active_sessions.pop(session_id, None)
        if not session:
            return None

        session.is_recording = False

        if not session.frames:
            return None

        try:
            await self._save_video(session)
            file_size = os.path.getsize(session.filepath) if os.path.exists(session.filepath) else 0
            duration = len(session.frames) / session.fps

            return {
                "session_id": session.session_id,
                "camera_id": session.camera_id,
                "filename": os.path.basename(session.filepath),
                "filepath": session.filepath,
                "duration": round(duration, 2),
                "file_size": file_size,
                "frame_count": session.frame_count,
                "fps": session.fps,
                "resolution": f"{session.resolution[0]}x{session.resolution[1]}",
                "started_at": session.started_at.isoformat(),
                "ended_at": datetime.now(timezone.utc).isoformat(),
            }
        except Exception as e:
            print(f"[Recording] Error saving: {e}")
            return None

    async def _save_video(self, session: RecordingSession):
        fourcc = cv2.VideoWriter_fourcc(*'MJPG')
        writer = cv2.VideoWriter(session.filepath, fourcc, session.fps, session.resolution)

        for frame in session.frames:
            writer.write(frame)

        writer.release()

    async def capture_and_record(self, camera_id: str, frame: np.ndarray, duration_seconds: float = 10.0, fps: float = 15.0) -> Optional[dict]:
        session_id = await self.start_recording(camera_id, fps)
        frame_interval = 1.0 / fps
        frames_needed = int(duration_seconds * fps)

        for _ in range(frames_needed):
            await self.write_frame(session_id, frame)
            await asyncio.sleep(frame_interval)

        return await self.stop_recording(session_id)

    async def cleanup_old_recordings(self):
        cutoff = datetime.now(timezone.utc) - timedelta(days=self.max_days)
        deleted = 0

        for filepath in self.recording_dir.glob("*.avi"):
            try:
                mtime = datetime.fromtimestamp(filepath.stat().st_mtime, tz=timezone.utc)
                if mtime < cutoff:
                    filepath.unlink()
                    deleted += 1
            except Exception:
                pass

        for filepath in self.recording_dir.glob("*.jpg"):
            try:
                mtime = datetime.fromtimestamp(filepath.stat().st_mtime, tz=timezone.utc)
                if mtime < cutoff:
                    filepath.unlink()
                    deleted += 1
            except Exception:
                pass

        return deleted

    def get_storage_info(self) -> dict:
        total_size = 0
        file_count = 0
        for f in self.recording_dir.iterdir():
            if f.is_file():
                total_size += f.stat().st_size
                file_count += 1

        if total_size > 1024 * 1024 * 1024:
            size_str = f"{total_size / (1024**3):.2f} GB"
        elif total_size > 1024 * 1024:
            size_str = f"{total_size / (1024**2):.1f} MB"
        else:
            size_str = f"{total_size / 1024:.1f} KB"

        return {
            "total_size_bytes": total_size,
            "total_size": size_str,
            "file_count": file_count,
            "recording_dir": str(self.recording_dir),
            "max_days": self.max_days,
        }

    async def start_periodic_cleanup(self, interval_hours: int = 24):
        async def _cleanup_loop():
            while True:
                try:
                    deleted = await self.cleanup_old_recordings()
                    if deleted > 0:
                        print(f"[Recording] Cleaned up {deleted} old files")
                except Exception as e:
                    print(f"[Recording] Cleanup error: {e}")
                await asyncio.sleep(interval_hours * 3600)

        self._cleanup_task = asyncio.create_task(_cleanup_loop())

    def stop_periodic_cleanup(self):
        if self._cleanup_task:
            self._cleanup_task.cancel()
            self._cleanup_task = None


recording_engine = RecordingEngine()

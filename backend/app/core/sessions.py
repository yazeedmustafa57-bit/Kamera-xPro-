import time
import uuid
from typing import Dict, Optional, Set
from dataclasses import dataclass, field
from datetime import datetime, timezone
import asyncio


@dataclass
class UserSession:
    session_id: str
    user_id: str
    created_at: float
    last_active: float
    ip_address: str
    user_agent: str
    is_active: bool = True


class SessionManager:
    def __init__(self, max_sessions_per_user: int = 5, session_timeout: int = 86400):
        self.sessions: Dict[str, UserSession] = {}
        self.user_sessions: Dict[str, Set[str]] = {}
        self.max_sessions_per_user = max_sessions_per_user
        self.session_timeout = session_timeout

    def create_session(self, user_id: str, ip_address: str, user_agent: str) -> str:
        session_id = str(uuid.uuid4())
        now = time.time()

        session = UserSession(
            session_id=session_id,
            user_id=user_id,
            created_at=now,
            last_active=now,
            ip_address=ip_address,
            user_agent=user_agent,
        )
        self.sessions[session_id] = session

        if user_id not in self.user_sessions:
            self.user_sessions[user_id] = set()
        self.user_sessions[user_id].add(session_id)

        # Enforce max sessions per user
        user_sess = self.user_sessions[user_id]
        while len(user_sess) > self.max_sessions_per_user:
            oldest_id = min(user_sess, key=lambda s: self.sessions[s].last_active)
            self.revoke_session(oldest_id)

        return session_id

    def validate_session(self, session_id: str) -> Optional[UserSession]:
        session = self.sessions.get(session_id)
        if not session or not session.is_active:
            return None
        if time.time() - session.last_active > self.session_timeout:
            self.revoke_session(session_id)
            return None
        session.last_active = time.time()
        return session

    def revoke_session(self, session_id: str):
        session = self.sessions.pop(session_id, None)
        if session:
            session.is_active = False
            if session.user_id in self.user_sessions:
                self.user_sessions[session.user_id].discard(session_id)
                if not self.user_sessions[session.user_id]:
                    del self.user_sessions[session.user_id]

    def revoke_all_user_sessions(self, user_id: str):
        session_ids = list(self.user_sessions.get(user_id, set()))
        for sid in session_ids:
            self.revoke_session(sid)

    def get_user_sessions(self, user_id: str) -> list:
        result = []
        for sid in self.user_sessions.get(user_id, set()):
            session = self.sessions.get(sid)
            if session and session.is_active:
                result.append({
                    "session_id": session.session_id,
                    "created_at": datetime.fromtimestamp(session.created_at, tz=timezone.utc).isoformat(),
                    "last_active": datetime.fromtimestamp(session.last_active, tz=timezone.utc).isoformat(),
                    "ip_address": session.ip_address,
                    "user_agent": session.user_agent,
                })
        return result

    def cleanup_expired(self):
        now = time.time()
        expired = [
            sid for sid, session in self.sessions.items()
            if now - session.last_active > self.session_timeout
        ]
        for sid in expired:
            self.revoke_session(sid)
        return len(expired)

    @property
    def active_count(self) -> int:
        return sum(1 for s in self.sessions.values() if s.is_active)


session_manager = SessionManager()

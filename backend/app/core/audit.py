import json
import time
from datetime import datetime, timezone
from typing import Optional
from pathlib import Path
from dataclasses import dataclass, asdict
from fastapi import Request


@dataclass
class AuditEntry:
    timestamp: str
    user_id: Optional[str]
    username: Optional[str]
    action: str
    resource: str
    method: str
    path: str
    status_code: int
    ip_address: str
    user_agent: str
    details: Optional[dict] = None


class AuditLogger:
    def __init__(self, log_dir: str = "logs/audit"):
        self.log_dir = Path(log_dir)
        self.log_dir.mkdir(parents=True, exist_ok=True)
        self._buffer: list = []
        self._buffer_size = 50
        self._flush_interval = 30  # seconds
        self._last_flush = time.time()

    def _get_log_file(self) -> Path:
        date_str = datetime.now(timezone.utc).strftime("%Y-%m-%d")
        return self.log_dir / f"audit_{date_str}.jsonl"

    def log(self, entry: AuditEntry):
        self._buffer.append(asdict(entry))

        if len(self._buffer) >= self._buffer_size:
            self._flush()

        if time.time() - self._last_flush > self._flush_interval:
            self._flush()

    def _flush(self):
        if not self._buffer:
            return
        try:
            log_file = self._get_log_file()
            with open(log_file, "a") as f:
                for entry in self._buffer:
                    f.write(json.dumps(entry) + "\n")
            self._buffer.clear()
            self._last_flush = time.time()
        except Exception as e:
            print(f"[Audit] Flush error: {e}")

    def create_entry(
        self, user_id: Optional[str], username: Optional[str],
        action: str, resource: str, method: str, path: str,
        status_code: int, request: Optional[Request] = None,
        details: Optional[dict] = None,
    ) -> AuditEntry:
        ip_address = "unknown"
        user_agent = "unknown"

        if request:
            forwarded = request.headers.get("X-Forwarded-For")
            if forwarded:
                ip_address = forwarded.split(",")[0].strip()
            elif request.client:
                ip_address = request.client.host
            user_agent = request.headers.get("User-Agent", "unknown")

        entry = AuditEntry(
            timestamp=datetime.now(timezone.utc).isoformat(),
            user_id=user_id,
            username=username,
            action=action,
            resource=resource,
            method=method,
            path=path,
            status_code=status_code,
            ip_address=ip_address,
            user_agent=user_agent,
            details=details,
        )
        self.log(entry)
        return entry

    def query(self, user_id: Optional[str] = None, action: Optional[str] = None,
              limit: int = 100) -> list:
        results = []
        for log_file in sorted(self.log_dir.glob("audit_*.jsonl"), reverse=True):
            try:
                with open(log_file) as f:
                    for line in f:
                        entry = json.loads(line.strip())
                        if user_id and entry.get("user_id") != user_id:
                            continue
                        if action and entry.get("action") != action:
                            continue
                        results.append(entry)
                        if len(results) >= limit:
                            return results
            except Exception:
                pass
        return results

    def force_flush(self):
        self._flush()


audit_logger = AuditLogger()

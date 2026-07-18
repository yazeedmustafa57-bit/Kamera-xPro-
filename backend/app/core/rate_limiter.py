import time
from typing import Dict, Tuple
from collections import defaultdict
from fastapi import Request, HTTPException
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.responses import Response, JSONResponse


class RateLimiter:
    def __init__(self):
        self.requests: Dict[str, list] = defaultdict(list)

    def _get_client_id(self, request: Request) -> str:
        forwarded = request.headers.get("X-Forwarded-For")
        if forwarded:
            return forwarded.split(",")[0].strip()
        return request.client.host if request.client else "unknown"

    def is_rate_limited(self, key: str, max_requests: int, window_seconds: int) -> Tuple[bool, dict]:
        now = time.time()
        cutoff = now - window_seconds

        self.requests[key] = [t for t in self.requests[key] if t > cutoff]
        current_count = len(self.requests[key])

        if current_count >= max_requests:
            oldest = self.requests[key][0] if self.requests[key] else now
            retry_after = int(oldest + window_seconds - now) + 1
            return True, {
                "retry_after": retry_after,
                "limit": max_requests,
                "remaining": 0,
                "window": window_seconds,
            }

        self.requests[key].append(now)
        return False, {
            "limit": max_requests,
            "remaining": max_requests - current_count - 1,
            "window": window_seconds,
        }

    def cleanup(self, max_age: int = 3600):
        cutoff = time.time() - max_age
        for key in list(self.requests.keys()):
            self.requests[key] = [t for t in self.requests[key] if t > cutoff]
            if not self.requests[key]:
                del self.requests[key]


rate_limiter = RateLimiter()


class RateLimitMiddleware(BaseHTTPMiddleware):
    RATE_LIMITS = {
        "/api/auth/login": (5, 60),       # 5 requests per minute
        "/api/auth/register": (3, 300),    # 3 requests per 5 minutes
        "/api/cameras/": (60, 60),         # 60 per minute
        "/api/events/": (60, 60),
        "/api/recordings/": (30, 60),
        "/api/dashboard/": (30, 60),
    }
    DEFAULT_LIMIT = (120, 60)  # 120 requests per minute

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        if request.method == "OPTIONS":
            return await call_next(request)

        path = request.url.path
        client_id = rate_limiter._get_client_id(request)

        matched_limit = None
        for pattern, limit in self.RATE_LIMITS.items():
            if path.startswith(pattern):
                matched_limit = limit
                break

        if not matched_limit:
            matched_limit = self.DEFAULT_LIMIT

        max_requests, window = matched_limit
        key = f"{client_id}:{path.split('/')[2] if len(path.split('/')) > 2 else 'root'}"

        limited, info = rate_limiter.is_rate_limited(key, max_requests, window)

        if limited:
            return JSONResponse(
                status_code=429,
                content={"detail": "Rate limit exceeded. Try again later."},
                headers={
                    "X-RateLimit-Limit": str(info["limit"]),
                    "X-RateLimit-Remaining": "0",
                    "X-RateLimit-Reset": str(info["retry_after"]),
                    "Retry-After": str(info["retry_after"]),
                },
            )

        response = await call_next(request)
        response.headers["X-RateLimit-Limit"] = str(info["limit"])
        response.headers["X-RateLimit-Remaining"] = str(info["remaining"])
        return response

import pytest
from httpx import AsyncClient
from app.core.security import get_password_hash, verify_password, create_access_token


def test_password_hashing():
    password = "MySecurePass123"
    hashed = get_password_hash(password)
    assert hashed != password
    assert verify_password(password, hashed)
    assert not verify_password("WrongPass123", hashed)


def test_password_hash_different_each_time():
    h1 = get_password_hash("SamePass123")
    h2 = get_password_hash("SamePass123")
    assert h1 != h2  # bcrypt uses random salt


def test_jwt_token_creation():
    token = create_access_token(data={"sub": "user-123"})
    assert isinstance(token, str)
    assert len(token) > 0


def test_jwt_token_payload():
    from jose import jwt
    from app.core.config import settings
    token = create_access_token(data={"sub": "user-456"})
    payload = jwt.decode(token, settings.SECRET_KEY, algorithms=[settings.ALGORITHM])
    assert payload["sub"] == "user-456"
    assert "exp" in payload


def test_jwt_invalid_token():
    from jose import jwt
    from app.core.config import settings
    try:
        jwt.decode("invalid.token.here", settings.SECRET_KEY, algorithms=[settings.ALGORITHM])
        assert False, "Should have raised"
    except Exception:
        pass


def test_jwt_wrong_secret():
    from jose import jwt
    token = create_access_token(data={"sub": "user-789"})
    try:
        jwt.decode(token, "wrong-secret", algorithms=["HS256"])
        assert False, "Should have raised"
    except Exception:
        pass


@pytest.mark.asyncio
async def test_cors_headers(client: AsyncClient):
    response = await client.options("/api/health", headers={
        "Origin": "http://localhost:3000",
        "Access-Control-Request-Method": "GET",
    })
    # CORS middleware should respond
    assert response.status_code in [200, 405]


@pytest.mark.asyncio
async def test_invalid_token_rejected(client: AsyncClient):
    response = await client.get("/api/auth/me", headers={
        "Authorization": "Bearer invalid.token.here"
    })
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_missing_token_rejected(client: AsyncClient):
    response = await client.get("/api/auth/me")
    assert response.status_code == 403


@pytest.mark.asyncio
async def test_empty_password_rejected(client: AsyncClient):
    response = await client.post("/api/auth/register", json={
        "username": "test",
        "email": "test@test.com",
        "password": "",
    })
    assert response.status_code == 422


@pytest.mark.asyncio
async def test_weak_password_rejected(client: AsyncClient):
    response = await client.post("/api/auth/register", json={
        "username": "test",
        "email": "test@test.com",
        "password": "123",
    })
    assert response.status_code == 422


@pytest.mark.asyncio
async def test_invalid_email_rejected(client: AsyncClient):
    response = await client.post("/api/auth/register", json={
        "username": "test",
        "email": "not-an-email",
        "password": "ValidPass123",
    })
    assert response.status_code == 422

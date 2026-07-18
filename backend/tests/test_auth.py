import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_register_user(client: AsyncClient):
    response = await client.post("/api/auth/register", json={
        "username": "newuser",
        "email": "new@example.com",
        "password": "NewPass123",
        "role": "user",
    })
    assert response.status_code == 201
    data = response.json()
    assert "access_token" in data
    assert data["user"]["username"] == "newuser"
    assert data["user"]["email"] == "new@example.com"
    assert data["user"]["role"] == "user"


@pytest.mark.asyncio
async def test_register_prevents_admin(client: AsyncClient):
    response = await client.post("/api/auth/register", json={
        "username": "sneaky",
        "email": "sneaky@example.com",
        "password": "SneakyPass123",
        "role": "admin",
    })
    assert response.status_code == 201
    assert response.json()["user"]["role"] == "user"


@pytest.mark.asyncio
async def test_register_duplicate_username(client: AsyncClient):
    await client.post("/api/auth/register", json={
        "username": "duplicate",
        "email": "dup1@example.com",
        "password": "DupPass123",
    })
    response = await client.post("/api/auth/register", json={
        "username": "duplicate",
        "email": "dup2@example.com",
        "password": "DupPass123",
    })
    assert response.status_code == 400


@pytest.mark.asyncio
async def test_register_weak_password(client: AsyncClient):
    response = await client.post("/api/auth/register", json={
        "username": "weakuser",
        "email": "weak@example.com",
        "password": "short",
    })
    assert response.status_code == 422


@pytest.mark.asyncio
async def test_login_success(client: AsyncClient):
    await client.post("/api/auth/register", json={
        "username": "loginuser",
        "email": "login@example.com",
        "password": "LoginPass123",
    })
    response = await client.post("/api/auth/login", json={
        "username": "loginuser",
        "password": "LoginPass123",
    })
    assert response.status_code == 200
    assert "access_token" in response.json()


@pytest.mark.asyncio
async def test_login_wrong_password(client: AsyncClient):
    await client.post("/api/auth/register", json={
        "username": "wrongpw",
        "email": "wrongpw@example.com",
        "password": "RightPass123",
    })
    response = await client.post("/api/auth/login", json={
        "username": "wrongpw",
        "password": "WrongPass123",
    })
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_login_nonexistent_user(client: AsyncClient):
    response = await client.post("/api/auth/login", json={
        "username": "ghost",
        "password": "GhostPass123",
    })
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_get_me(client: AsyncClient, auth_headers):
    response = await client.get("/api/auth/me", headers=auth_headers)
    assert response.status_code == 200
    assert response.json()["username"] == "testuser"


@pytest.mark.asyncio
async def test_get_me_unauthorized(client: AsyncClient):
    response = await client.get("/api/auth/me")
    assert response.status_code == 403


@pytest.mark.asyncio
async def test_list_users_requires_admin(client: AsyncClient, auth_headers):
    response = await client.get("/api/auth/users", headers=auth_headers)
    assert response.status_code == 403


@pytest.mark.asyncio
async def test_list_users_admin(client: AsyncClient, admin_headers):
    response = await client.get("/api/auth/users", headers=admin_headers)
    assert response.status_code == 200
    assert isinstance(response.json(), list)


@pytest.mark.asyncio
async def test_delete_user_requires_admin(client: AsyncClient, auth_headers):
    response = await client.delete("/api/auth/users/some-id", headers=auth_headers)
    assert response.status_code == 403

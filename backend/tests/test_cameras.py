import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_create_camera(client: AsyncClient, auth_headers):
    response = await client.post("/api/cameras/", json={
        "name": "Front Door",
        "location": "Entrance",
    }, headers=auth_headers)
    assert response.status_code == 201
    data = response.json()
    assert data["name"] == "Front Door"
    assert data["location"] == "Entrance"
    assert data["status"] == "offline"
    assert data["battery"] == 100
    assert data["is_active"] is True


@pytest.mark.asyncio
async def test_list_cameras(client: AsyncClient, auth_headers):
    await client.post("/api/cameras/", json={"name": "Cam1"}, headers=auth_headers)
    await client.post("/api/cameras/", json={"name": "Cam2"}, headers=auth_headers)
    response = await client.get("/api/cameras/", headers=auth_headers)
    assert response.status_code == 200
    assert len(response.json()) == 2


@pytest.mark.asyncio
async def test_get_camera(client: AsyncClient, auth_headers):
    create_res = await client.post("/api/cameras/", json={"name": "MyCam"}, headers=auth_headers)
    cam_id = create_res.json()["id"]
    response = await client.get(f"/api/cameras/{cam_id}", headers=auth_headers)
    assert response.status_code == 200
    assert response.json()["name"] == "MyCam"


@pytest.mark.asyncio
async def test_update_camera(client: AsyncClient, auth_headers):
    create_res = await client.post("/api/cameras/", json={"name": "Old"}, headers=auth_headers)
    cam_id = create_res.json()["id"]
    response = await client.put(f"/api/cameras/{cam_id}", json={"name": "New"}, headers=auth_headers)
    assert response.status_code == 200
    assert response.json()["name"] == "New"


@pytest.mark.asyncio
async def test_toggle_camera(client: AsyncClient, auth_headers):
    create_res = await client.post("/api/cameras/", json={"name": "Toggle"}, headers=auth_headers)
    cam_id = create_res.json()["id"]
    response = await client.post(f"/api/cameras/{cam_id}/toggle", headers=auth_headers)
    assert response.status_code == 200
    assert response.json()["is_active"] is True
    assert response.json()["status"] == "online"


@pytest.mark.asyncio
async def test_delete_camera(client: AsyncClient, auth_headers):
    create_res = await client.post("/api/cameras/", json={"name": "Delete"}, headers=auth_headers)
    cam_id = create_res.json()["id"]
    response = await client.delete(f"/api/cameras/{cam_id}", headers=auth_headers)
    assert response.status_code == 200
    # Verify deleted
    get_res = await client.get(f"/api/cameras/{cam_id}", headers=auth_headers)
    assert get_res.status_code == 404


@pytest.mark.asyncio
async def test_camera_not_found(client: AsyncClient, auth_headers):
    response = await client.get("/api/cameras/00000000-0000-0000-0000-000000000000", headers=auth_headers)
    assert response.status_code == 404


@pytest.mark.asyncio
async def test_camera_requires_auth(client: AsyncClient):
    response = await client.get("/api/cameras/")
    assert response.status_code == 403


@pytest.mark.asyncio
async def test_camera_cannot_access_others(client: AsyncClient, auth_headers):
    # Register another user
    other = await client.post("/api/auth/register", json={
        "username": "other",
        "email": "other@example.com",
        "password": "OtherPass123",
    })
    other_headers = {"Authorization": f"Bearer {other.json()['access_token']}"}

    # Create camera as first user
    create_res = await client.post("/api/cameras/", json={"name": "Mine"}, headers=auth_headers)
    cam_id = create_res.json()["id"]

    # Other user cannot access
    response = await client.get(f"/api/cameras/{cam_id}", headers=other_headers)
    assert response.status_code == 404

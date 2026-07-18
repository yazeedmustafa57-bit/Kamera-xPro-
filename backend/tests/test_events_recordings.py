import pytest
from httpx import AsyncClient


@pytest.mark.asyncio
async def test_list_events_empty(client: AsyncClient, auth_headers):
    response = await client.get("/api/events/", headers=auth_headers)
    assert response.status_code == 200
    assert response.json() == []


@pytest.mark.asyncio
async def test_list_recordings_empty(client: AsyncClient, auth_headers):
    response = await client.get("/api/recordings/", headers=auth_headers)
    assert response.status_code == 200
    assert response.json() == []


@pytest.mark.asyncio
async def test_events_requires_auth(client: AsyncClient):
    response = await client.get("/api/events/")
    assert response.status_code == 403


@pytest.mark.asyncio
async def test_recordings_requires_auth(client: AsyncClient):
    response = await client.get("/api/recordings/")
    assert response.status_code == 403


@pytest.mark.asyncio
async def test_delete_event_ownership(client: AsyncClient, auth_headers):
    # User cannot delete non-existent event
    response = await client.delete(
        "/api/events/00000000-0000-0000-0000-000000000000",
        headers=auth_headers
    )
    assert response.status_code == 404


@pytest.mark.asyncio
async def test_delete_recording_ownership(client: AsyncClient, auth_headers):
    response = await client.delete(
        "/api/recordings/00000000-0000-0000-0000-000000000000",
        headers=auth_headers
    )
    assert response.status_code == 404

import pytest
import asyncio
from httpx import AsyncClient, ASGITransport
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy import select

from app.main import app
from app.core.database import Base, get_db
from app.core.security import get_password_hash
from app.models.user import User, UserRole

TEST_DATABASE_URL = "sqlite+aiosqlite:///./test.db"

test_engine = create_async_engine(TEST_DATABASE_URL, echo=False)
TestSessionLocal = async_sessionmaker(test_engine, class_=AsyncSession, expire_on_commit=False)


async def override_get_db():
    async with TestSessionLocal() as session:
        try:
            yield session
        finally:
            await session.close()

app.dependency_overrides[get_db] = override_get_db


@pytest.fixture(scope="session")
def event_loop():
    loop = asyncio.new_event_loop()
    yield loop
    loop.close()


@pytest.fixture(autouse=True)
async def setup_db():
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


@pytest.fixture
async def client():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


@pytest.fixture
async def auth_headers(client):
    response = await client.post("/api/auth/register", json={
        "username": "testuser",
        "email": "test@example.com",
        "password": "TestPass123",
        "role": "user",
    })
    token = response.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture
async def admin_headers(client):
    response = await client.post("/api/auth/register", json={
        "username": "testadmin",
        "email": "admin@example.com",
        "password": "AdminPass123",
        "role": "user",
    })
    token = response.json()["access_token"]
    headers = {"Authorization": f"Bearer {token}"}

    async with TestSessionLocal() as session:
        result = await session.execute(select(User).where(User.username == "testadmin"))
        user = result.scalar_one()
        user.role = UserRole.ADMIN
        await session.commit()

    return headers

from collections.abc import AsyncIterator
from pathlib import Path
import httpx
import pytest_asyncio
from app.config import Settings
from app.main import create_app


@pytest_asyncio.fixture
async def client(tmp_path: Path) -> AsyncIterator[httpx.AsyncClient]:
    db_file = tmp_path / "test_jobly.db"
    storage_dir = tmp_path / "storage"
    settings = Settings(
        database_url=f"sqlite+aiosqlite:///{db_file.as_posix()}",
        storage_dir=str(storage_dir),
        enable_scheduler=False,
    )
    app = create_app(settings_override=settings)
    async with app.router.lifespan_context(app):
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as ac:
            yield ac

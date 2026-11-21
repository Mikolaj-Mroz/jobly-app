from pathlib import Path
import httpx
import pytest
from app.config import Settings
from app.main import create_app


@pytest.mark.asyncio
async def test_notifications_list_and_mark_read(client):
    resp = await client.get("/notifications")
    assert resp.status_code == 200
    items = resp.json()["notifications"]
    assert len(items) == 3
    assert all(not item["is_read"] for item in items)

    mark = await client.patch("/notifications/notif_1/read")
    assert mark.status_code == 200
    assert mark.json() == {"id": "notif_1", "is_read": True}

    after = await client.get("/notifications")
    by_id = {item["id"]: item for item in after.json()["notifications"]}
    assert by_id["notif_1"]["is_read"] is True
    assert by_id["notif_2"]["is_read"] is False


@pytest.mark.asyncio
async def test_daily_digest_notification_avoids_duplicates_and_respects_settings(client):
    first = await client.post("/notifications/daily-digest")
    assert first.status_code == 200
    first_json = first.json()
    assert first_json["pushed"] is True
    assert "Daily best match" in first_json["notification"]["heading"]
    first_offer_id = first_json["notification"]["offer"]["id"]

    # Second run selects a different offer that hasn't been notified yet
    second = await client.post("/notifications/daily-digest")
    assert second.status_code == 200
    second_json = second.json()
    assert second_json["pushed"] is True
    assert second_json["notification"]["offer"]["id"] != first_offer_id

    # Disable daily digest in user settings -> blocked unless force=True
    disable = await client.put("/user/settings", json={"daily_digest_enabled": False})
    assert disable.status_code == 200
    assert disable.json()["daily_digest_enabled"] is False

    skipped = await client.post("/notifications/daily-digest")
    assert skipped.status_code == 200
    assert skipped.json()["pushed"] is False
    assert skipped.json()["notification"] is None

    forced = await client.post("/notifications/daily-digest", params={"force": True})
    assert forced.status_code == 200
    assert forced.json()["pushed"] is True


@pytest.mark.asyncio
async def test_daily_digest_scheduler_status(tmp_path: Path):
    settings = Settings(
        database_url=f"sqlite+aiosqlite:///{(tmp_path / 'digest_sched.db').as_posix()}",
        storage_dir=str(tmp_path / "storage"),
        enable_scheduler=True,
        scrape_on_startup=False,
        enable_daily_notification=True,
        daily_notification_interval_hours=24,
    )
    app = create_app(settings_override=settings)
    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            status = await client.get("/notifications/digest/status")
            assert status.status_code == 200
            payload = status.json()
            assert payload["scheduler_enabled"] is True
            assert payload["daily_enabled"] is True
            assert payload["interval_hours"] == 24
            assert payload["next_run_at"] is not None

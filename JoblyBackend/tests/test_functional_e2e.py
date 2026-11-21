"""End-to-end black-box functional tests for JoblyBackend HTTP API.

Exercises full multi-step user journeys through the real FastAPI app, real
SQLite persistence, real PDF creation/parsing, real recommendation ranking,
and adversarial HTTP boundary cases.
"""

from __future__ import annotations

import httpx
import pytest
from fpdf import FPDF


def _pdf_bytes(lines: list[str], pages: int = 1) -> bytes:
    pdf = FPDF()
    for i in range(pages):
        pdf.add_page()
        pdf.set_font("Helvetica", size=11)
        for line in lines:
            pdf.cell(0, 8, text=line if i == 0 else f"Continuation page {i + 1}", new_x="LMARGIN", new_y="NEXT")
    return bytes(pdf.output())


@pytest.mark.asyncio
async def test_e2e_user_onboarding_cv_location_sync_and_geo_ranking(client: httpx.AsyncClient) -> None:
    # 1. Update profile & settings (onboarding journey)
    profile_resp = await client.put(
        "/user/profile",
        json={
            "name": "Karolina Zielinska",
            "phone": "+48 511 222 333",
            "email": "karolina@example.com",
        },
    )
    assert profile_resp.status_code == 200
    assert profile_resp.json()["name"] == "Karolina Zielinska"

    settings_resp = await client.put(
        "/user/settings",
        json={
            "daily_digest_enabled": True,
            "daily_digest_hour": 9,
            "preferred_work_mode": "Remote",
            "city": "Krakow",
        },
    )
    assert settings_resp.status_code == 200
    assert settings_resp.json()["city"] == "Krakow"

    # 2. Upload a real PDF CV located in Gdansk -> auto-updates user location & nearby ordering
    gdansk_pdf = _pdf_bytes([
        "Karolina Zielinska",
        "Location: Gdansk, Poland",
        "Senior QA & Python Engineer",
        "Skills: Kubernetes, Linux, Python, ELK",
    ])
    upload_resp = await client.post(
        "/user/cvs/upload",
        files={"file": ("Karolina-Gdansk.pdf", gdansk_pdf, "application/pdf")},
    )
    assert upload_resp.status_code in (200, 201)
    uploaded_cv = upload_resp.json()
    assert uploaded_cv["file_name"] == "Karolina-Gdansk.pdf"
    assert uploaded_cv["page_count"] == 1

    # Verify settings city auto-updated to Gdansk
    settings_after_gdansk = (await client.get("/user/settings")).json()
    assert settings_after_gdansk["city"] == "Gdansk"

    # Verify /offers/around-you now ranks Gdansk offer (best_3) closest
    nearby = (await client.get("/offers/around-you")).json()["offers"]
    assert len(nearby) > 0
    assert nearby[0]["id"] == "best_3"

    # 3. Upload a second PDF with NO location info -> must skip updating location (stays Gdansk)
    no_loc_pdf = _pdf_bytes([
        "Karolina Zielinska",
        "Skills: Rust, Distributed Systems, gRPC",
        "5 years building backend services.",
    ])
    upload_no_loc = await client.post(
        "/user/cvs/upload",
        files={"file": ("NoLocation.pdf", no_loc_pdf, "application/pdf")},
    )
    assert upload_no_loc.status_code in (200, 201)
    assert (await client.get("/user/settings")).json()["city"] == "Gdansk"

    # 4. Edit CV to move to Wroclaw -> user location updates to Wroclaw
    edit_resp = await client.put(
        f"/user/cvs/{uploaded_cv['id']}",
        json={"summary": "Relocated and currently based in Wroclaw, Poland."},
    )
    assert edit_resp.status_code == 200
    assert (await client.get("/user/settings")).json()["city"] == "Wroclaw"

    # 5. Download edited PDF and verify valid PDF bytes
    dl_resp = await client.get(f"/user/cvs/{uploaded_cv['id']}/download")
    assert dl_resp.status_code == 200
    assert dl_resp.headers["content-type"].startswith("application/pdf")
    assert dl_resp.content.startswith(b"%PDF-")

    # 6. Delete CV and confirm subsequent download/delete returns 404
    del_resp = await client.delete(f"/user/cvs/{uploaded_cv['id']}")
    assert del_resp.status_code in (200, 204)
    assert (await client.get(f"/user/cvs/{uploaded_cv['id']}/download")).status_code == 404
    assert (await client.delete(f"/user/cvs/{uploaded_cv['id']}")).status_code == 404


@pytest.mark.asyncio
async def test_e2e_cv_upload_security_and_limit_enforcement(client: httpx.AsyncClient) -> None:
    # Reject non-PDF file
    bad_type = await client.post(
        "/user/cvs/upload",
        files={"file": ("notes.txt", b"hello world", "text/plain")},
    )
    assert bad_type.status_code == 400

    # Reject corrupted PDF bytes
    corrupt_pdf = await client.post(
        "/user/cvs/upload",
        files={"file": ("broken.pdf", b"%PDF-1.4\nnot-really-a-pdf", "application/pdf")},
    )
    assert corrupt_pdf.status_code == 400

    # Reject >10-page PDF
    eleven_pages = _pdf_bytes(["CV line"], pages=11)
    too_long = await client.post(
        "/user/cvs/upload",
        files={"file": ("long.pdf", eleven_pages, "application/pdf")},
    )
    assert too_long.status_code == 400

    # Reject >5 MB PDF
    oversized = b"%PDF-1.4\n" + (b"X" * (5 * 1024 * 1024 + 64))
    too_big = await client.post(
        "/user/cvs/upload",
        files={"file": ("huge.pdf", oversized, "application/pdf")},
    )
    assert too_big.status_code in (400, 413)

    # Enforce 10 CVs per user max (seed starts with 1 CV -> 9 more succeed, 10th fails)
    valid_pdf = _pdf_bytes(["Valid Resume", "Python Developer"])
    existing = len((await client.get("/user/cvs")).json()["cvs"])
    for i in range(10 - existing):
        resp = await client.post(
            "/user/cvs/upload",
            files={"file": (f"cv_{i}.pdf", valid_pdf, "application/pdf")},
        )
        assert resp.status_code in (200, 201)

    overflow = await client.post(
        "/user/cvs/upload",
        files={"file": ("cv_overflow.pdf", valid_pdf, "application/pdf")},
    )
    assert overflow.status_code == 400


@pytest.mark.asyncio
async def test_e2e_apply_idempotency_daily_digest_and_notifications(client: httpx.AsyncClient) -> None:
    best_offers = (await client.get("/offers/best-for-you")).json()["offers"]
    assert len(best_offers) > 0
    for offer in best_offers:
        assert offer["match_score"] is not None
        assert 0 <= offer["match_score"] <= 100

    target_id = best_offers[0]["id"]

    # Apply twice -> idempotent, does not duplicate application
    first_apply = await client.post(f"/offers/{target_id}/apply", json={})
    second_apply = await client.post(f"/offers/{target_id}/apply", json={})
    assert first_apply.status_code == 200
    assert second_apply.status_code == 200

    apps = (await client.get("/user/applications")).json()["applications"]
    matching_apps = [a for a in apps if a.get("id") == target_id]
    assert len(matching_apps) == 1

    # Applying to nonexistent offer returns 404
    missing_apply = await client.post("/offers/does_not_exist_999/apply", json={})
    assert missing_apply.status_code == 404

    # Trigger daily best-match digest -> creates a notification
    digest_resp = await client.post("/notifications/daily-digest")
    assert digest_resp.status_code == 200
    digest_body = digest_resp.json()
    assert digest_body["pushed"] is True
    notif_id = digest_body["notification"]["id"]

    # Mark notification read -> idempotent and updates unread count
    before_notifs = (await client.get("/notifications")).json()["notifications"]
    unread_before = sum(1 for n in before_notifs if not n["is_read"])
    assert any(n["id"] == notif_id and not n["is_read"] for n in before_notifs)

    mark_1 = await client.patch(f"/notifications/{notif_id}/read")
    mark_2 = await client.patch(f"/notifications/{notif_id}/read")
    assert mark_1.status_code == 200
    assert mark_2.status_code == 200

    after_notifs = (await client.get("/notifications")).json()["notifications"]
    unread_after = sum(1 for n in after_notifs if not n["is_read"])
    assert unread_after == unread_before - 1

    # Unknown notification -> 404
    assert (await client.patch("/notifications/nonexistent_notif/read")).status_code == 404


@pytest.mark.asyncio
async def test_e2e_feedback_validation_and_adversarial_payloads(client: httpx.AsyncClient) -> None:
    # Blank message rejected
    bad_fb = await client.post(
        "/user/feedback",
        json={"category": "Bug", "message": "   "},
    )
    assert bad_fb.status_code in (400, 422)

    # SQL-injection-like & XSS-like strings safely persisted verbatim without errors
    adversarial_msg = "'; DROP TABLE job_offers; -- <script>alert(1)</script> 🚀"
    ok_fb = await client.post(
        "/user/feedback",
        json={"category": "Feature request", "message": adversarial_msg, "contact_email": "qa@example.com"},
    )
    assert ok_fb.status_code in (200, 201)
    assert ok_fb.json()["status"] == "received"

    # Offers table is completely intact after adversarial payload
    offers = (await client.get("/offers/best-for-you")).json()["offers"]
    assert len(offers) >= 1


@pytest.mark.asyncio
async def test_e2e_create_empty_cv_has_no_mock_template_data(client: httpx.AsyncClient) -> None:
    create_resp = await client.post("/user/cvs", json={"file_name": "Blank-Resume.pdf"})
    assert create_resp.status_code in (200, 201)
    cv_id = create_resp.json()["id"]

    dl_resp = await client.get(f"/user/cvs/{cv_id}/download")
    assert dl_resp.status_code == 200
    assert dl_resp.content.startswith(b"%PDF-")

    from app.services import pdf_service
    pages, extracted = pdf_service.validate_and_extract_pdf(
        dl_resp.content, max_bytes=5 * 1024 * 1024, max_pages=10
    )
    assert pages == 1
    assert "Quality Assurance Engineer" not in extracted
    assert "IT DEV COMPANY" not in extracted
    assert "Kubernetes" not in extracted
    assert "CloudTest Lab" not in extracted

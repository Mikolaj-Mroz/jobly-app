from io import BytesIO
from fpdf import FPDF
from pypdf import PdfReader
import pytest


@pytest.mark.asyncio
async def test_user_profile_get_and_update(client):
    resp = await client.get("/user/profile")
    assert resp.status_code == 200
    assert resp.json() == {
        "name": "John Workman",
        "phone": "+1 123 456 789",
        "email": "jworkman@mail.com",
    }

    updated = await client.put(
        "/user/profile",
        json={"name": "Jane Doe", "phone": "+48 500 600 700", "email": "jane@jobly.app"},
    )
    assert updated.status_code == 200
    assert updated.json()["name"] == "Jane Doe"

    after = await client.get("/user/profile")
    assert after.json()["email"] == "jane@jobly.app"


@pytest.mark.asyncio
async def test_create_edit_download_and_upload_cv_pdf(client):
    created = await client.post("/user/cvs", json={"file_name": "Backend-CV.pdf"})
    assert created.status_code == 200
    cv_id = created.json()["id"]
    assert created.json()["file_name"] == "Backend-CV.pdf"

    dl = await client.get(f"/user/cvs/{cv_id}/download")
    assert dl.status_code == 200
    assert dl.content.startswith(b"%PDF-")
    extracted = "\n".join(p.extract_text() or "" for p in PdfReader(BytesIO(dl.content)).pages)
    assert "John Workman" in extracted

    edited = await client.put(
        f"/user/cvs/{cv_id}",
        json={
            "headline": "Senior Backend Python Engineer",
            "summary": "FastAPI and PostgreSQL pgvector specialist.",
            "skills": ["FastAPI", "PostgreSQL", "pgvector", "Docker"],
        },
    )
    assert edited.status_code == 200

    dl_after = await client.get(f"/user/cvs/{cv_id}/download")
    assert dl_after.status_code == 200
    extracted_after = "\n".join(
        p.extract_text() or "" for p in PdfReader(BytesIO(dl_after.content)).pages
    )
    assert "Senior Backend Python Engineer" in extracted_after
    assert "pgvector" in extracted_after

    # Upload an arbitrary valid PDF via multipart upload
    pdf = FPDF()
    pdf.add_page()
    pdf.set_font("Helvetica", size=12)
    pdf.cell(0, 10, "Uploaded Resume Rust Async")
    pdf_bytes = bytes(pdf.output())

    uploaded = await client.post(
        "/user/cvs/upload",
        files={"file": ("Uploaded-Resume.pdf", pdf_bytes, "application/pdf")},
    )
    assert uploaded.status_code == 200
    assert uploaded.json()["file_name"] == "Uploaded-Resume.pdf"
    assert uploaded.json()["page_count"] == 1
    assert uploaded.json()["file_size_bytes"] == len(pdf_bytes)


@pytest.mark.asyncio
async def test_cv_upload_limits_validation_parsing_and_deletion(client):
    # 1. Non-PDF extension rejected
    bad_ext = await client.post(
        "/user/cvs/upload",
        files={"file": ("resume.txt", b"hello world", "text/plain")},
    )
    assert bad_ext.status_code == 400

    # 2. Invalid PDF bytes rejected even with .pdf filename
    bad_bytes = await client.post(
        "/user/cvs/upload",
        files={"file": ("fake.pdf", b"not a real pdf header", "application/pdf")},
    )
    assert bad_bytes.status_code == 400

    # 3. Oversized file (> 5 MB) rejected with 413
    oversized = b"%PDF-1.4\n" + (b"0" * (5 * 1024 * 1024 + 64))
    too_big = await client.post(
        "/user/cvs/upload",
        files={"file": ("huge.pdf", oversized, "application/pdf")},
    )
    assert too_big.status_code == 413

    # 4. PDF exceeding 10 pages rejected with 400
    long_pdf = FPDF()
    long_pdf.set_font("Helvetica", size=11)
    for page_num in range(1, 12):
        long_pdf.add_page()
        long_pdf.cell(0, 10, f"Page {page_num}")
    long_resp = await client.post(
        "/user/cvs/upload",
        files={"file": ("eleven-pages.pdf", bytes(long_pdf.output()), "application/pdf")},
    )
    assert long_resp.status_code == 400
    assert "10 pages" in long_resp.json()["detail"]

    # 5. Valid custom Rust PDF parses without hardcoded QA defaults and supports DELETE
    rust_pdf = FPDF()
    rust_pdf.add_page()
    rust_pdf.set_font("Helvetica", size=11)
    for line in [
        "Anna Nowak",
        "Rust Systems Engineer",
        "anna.nowak@rustdev.pl",
        "Summary",
        "Building low-latency async network services.",
        "Skills",
        "Rust, Tokio, PostgreSQL",
        "Education",
        "M.Sc. AGH University of Science",
    ]:
        rust_pdf.cell(0, 8, line, new_x="LMARGIN", new_y="NEXT")

    uploaded = await client.post(
        "/user/cvs/upload",
        files={"file": ("Anna-Nowak-Rust.pdf", bytes(rust_pdf.output()), "application/pdf")},
    )
    assert uploaded.status_code == 200
    rust_cv_id = uploaded.json()["id"]

    # Delete it and confirm download returns 404 afterwards
    deleted = await client.delete(f"/user/cvs/{rust_cv_id}")
    assert deleted.status_code == 200
    assert deleted.json() == {"deleted": True, "id": rust_cv_id}

    dl_deleted = await client.get(f"/user/cvs/{rust_cv_id}/download")
    assert dl_deleted.status_code == 404

    # 6. Enforces max_cvs_per_user = 10 (1 seeded + 9 created -> 10th creation blocked)
    for i in range(9):
        ok = await client.post("/user/cvs", json={"file_name": f"Extra-{i}.pdf"})
        assert ok.status_code == 200

    overflow = await client.post("/user/cvs", json={"file_name": "Extra-Overflow.pdf"})
    assert overflow.status_code == 400
    assert "Maximum limit" in overflow.json()["detail"]


@pytest.mark.asyncio
async def test_user_settings_and_feedback_endpoints(client):
    initial = await client.get("/user/settings")
    assert initial.status_code == 200
    assert initial.json()["city"] == "Krakow"
    assert initial.json()["onboarding_completed"] is False

    updated = await client.put(
        "/user/settings",
        json={
            "city": "Warszawa",
            "latitude": 52.2297,
            "longitude": 21.0122,
            "radius_km": 50.0,
            "currency": "eur",
            "job_alerts_enabled": True,
            "daily_digest_enabled": False,
            "onboarding_completed": True,
        },
    )
    assert updated.status_code == 200
    up_json = updated.json()
    assert up_json["city"] == "Warszawa"
    assert up_json["currency"] == "EUR"
    assert up_json["radius_km"] == 50.0
    assert up_json["daily_digest_enabled"] is False
    assert up_json["onboarding_completed"] is True

    after = await client.get("/user/settings")
    assert after.status_code == 200
    assert after.json()["city"] == "Warszawa"

    empty_fb = await client.post("/user/feedback", json={"category": "Bug", "message": "   "})
    assert empty_fb.status_code == 400

    valid_fb = await client.post(
        "/user/feedback",
        json={
            "category": "Feature",
            "message": "Love the daily job recommendations!",
            "contact_email": "user@jobly.app",
        },
    )
    assert valid_fb.status_code == 200
    fb_json = valid_fb.json()
    assert fb_json["status"] == "received"
    assert fb_json["category"] == "Feature"
    assert fb_json["id"].startswith("fb_")


@pytest.mark.asyncio
async def test_cv_upload_and_edit_extracts_location_or_skips_when_absent(client):
    # 1. Upload a PDF mentioning Gdansk -> updates user city & coordinates
    gdansk_pdf = FPDF()
    gdansk_pdf.add_page()
    gdansk_pdf.set_font("Helvetica", size=11)
    for line in [
        "Marek Lis",
        "Location: Gdansk, Poland",
        "Skills: Python, FastAPI",
    ]:
        gdansk_pdf.cell(0, 8, line, new_x="LMARGIN", new_y="NEXT")

    up1 = await client.post(
        "/user/cvs/upload",
        files={"file": ("Marek-Gdansk.pdf", bytes(gdansk_pdf.output()), "application/pdf")},
    )
    assert up1.status_code == 200
    cv_id = up1.json()["id"]

    settings1 = (await client.get("/user/settings")).json()
    assert settings1["city"] == "Gdansk"
    assert abs(settings1["latitude"] - 54.3520) < 0.01

    # 2. Upload a PDF with NO city info -> skips location change (stays Gdansk)
    noloc_pdf = FPDF()
    noloc_pdf.add_page()
    noloc_pdf.set_font("Helvetica", size=11)
    noloc_pdf.cell(0, 8, "Anonymous Dev - Python and Docker", new_x="LMARGIN", new_y="NEXT")

    up2 = await client.post(
        "/user/cvs/upload",
        files={"file": ("No-Location.pdf", bytes(noloc_pdf.output()), "application/pdf")},
    )
    assert up2.status_code == 200

    settings2 = (await client.get("/user/settings")).json()
    assert settings2["city"] == "Gdansk"

    # 3. Editing a CV to mention Wroclaw -> updates location to Wroclaw
    edit_resp = await client.put(
        f"/user/cvs/{cv_id}",
        json={"summary": "Senior Python developer based in Wroclaw."},
    )
    assert edit_resp.status_code == 200

    settings3 = (await client.get("/user/settings")).json()
    assert settings3["city"] == "Wroclaw"
    assert abs(settings3["latitude"] - 51.1079) < 0.01

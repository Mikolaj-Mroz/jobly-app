import json
import pytest
from fpdf import FPDF
from app.models import ApplicationModel, CvModel, JobOfferModel


@pytest.mark.asyncio
async def test_around_you_and_best_for_you_offers(client):
    around_resp = await client.get("/offers/around-you")
    assert around_resp.status_code == 200
    around_offers = around_resp.json()["offers"]
    assert len(around_offers) == 6
    # Krakow offers (close_1..3) come before distant cities
    assert around_offers[0]["id"].startswith("close_")
    assert around_offers[0]["external_url"]
    assert around_offers[0]["match_reasons"]

    best_resp = await client.get("/offers/best-for-you")
    assert best_resp.status_code == 200
    best_offers = best_resp.json()["offers"]
    assert len(best_offers) == 6
    assert best_offers[0]["match_score"] is not None
    assert best_offers[0]["match_reasons"]

    filtered = await client.get("/offers/around-you", params={"query": "Kubernetes"})
    assert filtered.status_code == 200
    assert len(filtered.json()["offers"]) == 6

    empty = await client.get("/offers/around-you", params={"query": "nonexistent_stack_xyz"})
    assert empty.status_code == 200
    assert empty.json()["offers"] == []


@pytest.mark.asyncio
async def test_apply_to_offer_records_and_deduplicates(client):
    first = await client.post("/offers/best_1/apply", json={"cv_id": "cv_1"})
    assert first.status_code == 200
    assert first.json()["applied"] is True
    assert first.json()["external_url"].startswith("https://justjoin.it/")

    duplicate = await client.post("/offers/best_1/apply", json={"cv_id": "cv_1"})
    assert duplicate.status_code == 200
    assert duplicate.json()["applied"] is False
    assert duplicate.json()["external_url"] == first.json()["external_url"]

    apps_resp = await client.get("/user/applications")
    assert apps_resp.status_code == 200
    app_ids = [o["id"] for o in apps_resp.json()["applications"]]
    assert "best_1" in app_ids
    assert "close_1" in app_ids


@pytest.mark.asyncio
async def test_recommendation_engine_ranks_aliases_seniority_geo_and_applied_novelty(client):
    # Upload a Python / FastAPI / K8s (alias!) Senior CV
    pdf = FPDF()
    pdf.add_page()
    pdf.set_font("Helvetica", size=11)
    for line in [
        "Ewa Zielinska",
        "Senior Python Backend Engineer",
        "Summary",
        "7 years designing FastAPI microservices with K8s and Postgres.",
        "Skills",
        "Python, FastAPI, K8s, Postgres",
    ]:
        pdf.cell(0, 8, line, new_x="LMARGIN", new_y="NEXT")

    upload_resp = await client.post(
        "/user/cvs/upload",
        files={"file": ("Ewa-Python.pdf", bytes(pdf.output()), "application/pdf")},
    )
    assert upload_resp.status_code == 200
    cv_id = upload_resp.json()["id"]

    # Insert diverse offers directly through the app session_maker
    from app.services import recommendation_service

    python_fresh = JobOfferModel(
        id="rec_py_fresh",
        initial="P",
        title="Senior Python Backend Engineer",
        salary_range="22 000 - 28 000",
        salary_unit="PLN/month",
        skills_json=json.dumps(["Python", "FastAPI", "Kubernetes", "PostgreSQL"]),
        company_name="PyScale",
        level="SENIOR",
        contract_type="B2B",
        work_schedule="Full-Time",
        accent="YELLOW",
        description="Remote backend services in Python, FastAPI and Kubernetes.",
        source="justjoin.it",
        external_url="https://justjoin.it/job-offer/pyscale-senior-python",
        latitude=50.0647,
        longitude=19.9450,
    )
    python_applied = JobOfferModel(
        id="rec_py_applied",
        initial="P",
        title="Senior Python Backend Engineer",
        salary_range="22 000 - 28 000",
        salary_unit="PLN/month",
        skills_json=json.dumps(["Python", "FastAPI", "Kubernetes", "PostgreSQL"]),
        company_name="PyScale Already Applied",
        level="SENIOR",
        contract_type="B2B",
        work_schedule="Full-Time",
        accent="BLUE",
        description="Remote backend services in Python, FastAPI and Kubernetes.",
        source="justjoin.it",
        external_url="https://justjoin.it/job-offer/pyscale-applied",
        latitude=50.0647,
        longitude=19.9450,
    )
    frontend_junior = JobOfferModel(
        id="rec_fe_junior",
        initial="F",
        title="Junior Frontend React Developer",
        salary_range="8 000 - 11 000",
        salary_unit="PLN/month",
        skills_json=json.dumps(["React", "TypeScript", "CSS"]),
        company_name="PixelWeb",
        level="JUNIOR",
        contract_type="UoP",
        work_schedule="Full-Time",
        accent="PINK",
        description="Build landing pages in Gdansk office.",
        source="justjoin.it",
        external_url="https://justjoin.it/job-offer/pixelweb-junior-react",
        latitude=54.3520,
        longitude=18.6466,
    )
    cv = CvModel(
        id=cv_id,
        user_id="default_user",
        file_name="Ewa-Python.pdf",
        file_path="/tmp/ewa.pdf",
        raw_text="Senior Python Backend Engineer 7 years FastAPI K8s Postgres",
        structured_json=json.dumps(
            {
                "full_name": "Ewa Zielinska",
                "headline": "Senior Python Backend Engineer",
                "summary": "7 years designing FastAPI microservices with K8s and Postgres.",
                "skills": ["Python", "FastAPI", "K8s", "Postgres"],
            }
        ),
    )

    scored = recommendation_service.score_best_for_you(
        offers=[frontend_junior, python_applied, python_fresh],
        cv=cv,
        user_lat=50.0647,
        user_lon=19.9450,
        applied_offers=[python_applied],
    )
    assert [s.offer.id for s in scored] == ["rec_py_fresh", "rec_py_applied", "rec_fe_junior"]
    assert scored[0].match_score >= 80
    assert scored[0].match_score > scored[1].match_score
    assert scored[0].match_score > scored[2].match_score
    reasons_text = " ".join(scored[0].match_reasons)
    assert "Python" in reasons_text
    assert "Kubernetes" in reasons_text
    assert "Remote-friendly" in scored[0].match_reasons

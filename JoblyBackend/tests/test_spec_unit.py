"""Specification-driven (black-box) unit tests for JoblyBackend.

These tests assert behavioral contracts, invariants, boundary conditions, and
adversarial input handling without coupling to internal implementation details.
"""

from __future__ import annotations

import json
import math
from pathlib import Path

import pytest
from fpdf import FPDF

from app.models import CvModel, JobOfferModel
from app.schemas import CvStructuredData
from app.services import pdf_service
from app.services.recommendation_service import rank_around_you, score_best_for_you
from app.services.scraper_service import JustJoinItScraper


def _make_cv(raw_text: str, headline: str = "", skills: list[str] | None = None) -> CvModel:
    structured = CvStructuredData(
        full_name="Test Candidate",
        headline=headline,
        summary=raw_text,
        skills=skills or [],
    )
    return CvModel(
        id="cv_test",
        user_id="default_user",
        file_name="test.pdf",
        updated_label="Updated today",
        file_path="test.pdf",
        raw_text=raw_text,
        structured_json=structured.model_dump_json(),
    )


def _make_pdf_bytes(lines: list[str], pages: int = 1) -> bytes:
    pdf = FPDF()
    pdf.set_auto_page_break(auto=True, margin=15)
    for page_idx in range(pages):
        pdf.add_page()
        pdf.set_font("Helvetica", size=11)
        for line in lines:
            pdf.cell(0, 8, text=f"{line}" if page_idx == 0 else f"Page {page_idx + 1}", new_x="LMARGIN", new_y="NEXT")
    return bytes(pdf.output())


def _make_offer(
    offer_id: str,
    title: str = "Software Engineer",
    skills: list[str] | None = None,
    level: str = "MID",
    description: str = "",
    lat: float | None = 50.0647,
    lon: float | None = 19.9450,
    company: str = "Acme",
) -> JobOfferModel:
    return JobOfferModel(
        id=offer_id,
        initial=company[:1].upper() or "A",
        title=title,
        salary_range="15 000 - 20 000",
        salary_unit="PLN/month",
        skills_json=json.dumps(skills if skills is not None else ["Python"]),
        company_name=company,
        level=level,
        contract_type="B2B",
        work_schedule="Full-Time",
        accent="YELLOW",
        description=description,
        source="justjoin.it",
        external_url=f"https://justjoin.it/job-offer/{offer_id}",
        latitude=lat,
        longitude=lon,
    )


# ---------------------------------------------------------------------------
# 1. Location extraction specification (boundary, Unicode, precedence, empty)
# ---------------------------------------------------------------------------


@pytest.mark.parametrize(
    "text,expected_city",
    [
        ("Jan Kowalski\nLocation: Kraków, Poland\nPython Developer", "Krakow"),
        ("Mieszkam w Krakowie i programuję w Pythonie.", "Krakow"),
        ("Anna Nowak\nLokalizacja: Gdańsk\nBackend Engineer", "Gdansk"),
        ("Software Engineer based in Wrocławiu, Poland", "Wroclaw"),
        ("Miasto: Łódź\nQA Automation", "Lodz"),
        ("Address: Poznań, ul. Półwiejska 10", "Poznan"),
        ("Senior DevOps Engineer\nWarszawa, Poland", "Warszawa"),
        ("Contact: Berlin, Germany\nCloud Architect", "Berlin"),
    ],
)
def test_location_extraction_recognizes_diacritics_locatives_and_labels(
    text: str, expected_city: str
) -> None:
    result = pdf_service.extract_location_from_cv_text(text)
    assert result is not None
    city, lat, lon = result
    assert city == expected_city
    assert -90.0 <= lat <= 90.0
    assert -180.0 <= lon <= 180.0


def test_location_extraction_explicit_label_beats_body_mention() -> None:
    cv_text = (
        "Jan Nowak\n"
        "Location: Gdansk, Poland\n\n"
        "Experience:\n"
        "- Built distributed microservices for a Warsaw banking client.\n"
        "- Collaborated with teams in Berlin and London.\n"
    )
    result = pdf_service.extract_location_from_cv_text(cv_text)
    assert result is not None
    assert result[0] == "Gdansk"


@pytest.mark.parametrize(
    "text",
    [
        "",
        "   \n\t  ",
        "Software Engineer\nSkills: Python, Docker, Kubernetes, PostgreSQL\n5 years experience",
        "12345 !@#$%^&*() 🚀🔥",
        "No geographic information provided anywhere in this resume.",
    ],
)
def test_location_extraction_returns_none_when_no_city_present(text: str) -> None:
    assert pdf_service.extract_location_from_cv_text(text) is None


# ---------------------------------------------------------------------------
# 2. PDF generation, validation, and boundary enforcement specification
# ---------------------------------------------------------------------------


def test_pdf_render_and_roundtrip_preserves_unicode_and_valid_header(tmp_path: Path) -> None:
    cv = CvStructuredData(
        full_name="Łukasz Żółć-Świątek",
        headline="Senior Backend Engineer — Kraków",
        email="lukasz@example.com",
        phone="+48 600 100 200",
        summary="Projektuję skalowalne systemy w Pythonie i Kubernetes.",
        skills=["Python", "Kubernetes", "PostgreSQL"],
        experience=["Lead Engineer @ TechCo (2022–2026)"],
        education="AGH Kraków — Informatyka",
    )
    out_file = tmp_path / "unicode_cv.pdf"
    pdf_service.render_cv_pdf(cv, out_file)

    raw_bytes = out_file.read_bytes()
    assert raw_bytes.startswith(b"%PDF-")
    pages, extracted = pdf_service.validate_and_extract_pdf(raw_bytes, max_bytes=5 * 1024 * 1024, max_pages=10)
    assert 1 <= pages <= 10
    assert "lukasz@example.com" in extracted
    assert "Kubernetes" in extracted


def test_pdf_validation_rejects_malformed_empty_oversized_and_too_many_pages() -> None:
    max_bytes = 5 * 1024 * 1024
    max_pages = 10

    # Empty bytes
    with pytest.raises(pdf_service.PdfValidationError):
        pdf_service.validate_and_extract_pdf(b"", max_bytes=max_bytes, max_pages=max_pages)

    # Non-PDF payload
    with pytest.raises(pdf_service.PdfValidationError):
        pdf_service.validate_and_extract_pdf(b"<html>not a pdf</html>", max_bytes=max_bytes, max_pages=max_pages)

    # Corrupted PDF header with garbage body
    with pytest.raises(pdf_service.PdfValidationError):
        pdf_service.validate_and_extract_pdf(b"%PDF-1.7\ncorrupted-garbage-bytes", max_bytes=max_bytes, max_pages=max_pages)

    # Oversized (> 5 MB)
    oversized = b"%PDF-1.4\n" + (b"0" * (max_bytes + 1))
    with pytest.raises(pdf_service.PdfValidationError):
        pdf_service.validate_and_extract_pdf(oversized, max_bytes=max_bytes, max_pages=max_pages)

    # Exceeds max page count (> 10 pages)
    too_many_pages = _make_pdf_bytes(["Resume line"], pages=max_pages + 1)
    with pytest.raises(pdf_service.PdfValidationError):
        pdf_service.validate_and_extract_pdf(too_many_pages, max_bytes=max_bytes, max_pages=max_pages)

    # Exactly max page count succeeds
    boundary_ok = _make_pdf_bytes(["Resume line"], pages=max_pages)
    page_count, text = pdf_service.validate_and_extract_pdf(boundary_ok, max_bytes=max_bytes, max_pages=max_pages)
    assert page_count == max_pages
    assert "Resume line" in text


@pytest.mark.parametrize(
    "dangerous_name",
    [
        "../../etc/passwd.pdf",
        "..\\..\\windows\\system32\\cmd.pdf",
        "   ",
        "my resume without extension",
        "a" * 250 + ".pdf",
    ],
)
def test_sanitize_pdf_filename_blocks_traversal_and_enforces_pdf_extension(
    dangerous_name: str,
) -> None:
    safe = pdf_service.sanitize_filename(dangerous_name)
    assert "/" not in safe
    assert "\\" not in safe
    assert ".." not in safe
    assert safe.lower().endswith(".pdf")
    assert 5 <= len(safe) <= 120


# ---------------------------------------------------------------------------
# 3. Recommendation engine invariants (bounds, monotonicity, aliasing, geo)
# ---------------------------------------------------------------------------


def test_recommendation_scores_always_bounded_0_to_100_even_with_extreme_inputs() -> None:
    offers = [
        _make_offer("o_empty", title="", skills=[], level="", description="", lat=None, lon=None),
        _make_offer(
            "o_huge",
            title="Principal Distributed Systems Architect",
            skills=["Python"] * 30,
            level="SENIOR",
            description="Python Kubernetes Docker AWS " * 200,
            lat=50.0647,
            lon=19.9450,
        ),
    ]
    for cv_obj in [None, _make_cv(""), _make_cv("   "), _make_cv("Python Kubernetes Docker AWS " * 500)]:
        ranked = score_best_for_you(offers=offers, cv=cv_obj, user_lat=50.0647, user_lon=19.9450)
        assert len(ranked) == len(offers)
        for item in ranked:
            assert item.match_score is not None
            assert 0 <= item.match_score <= 100
            assert isinstance(item.match_reasons, list)


def test_recommendation_monotonicity_matching_skills_beats_unrelated_offer() -> None:
    matching = _make_offer(
        "match",
        title="Senior Python Backend Engineer",
        skills=["Python", "FastAPI", "PostgreSQL", "Docker"],
        level="SENIOR",
        description="Build async Python APIs with FastAPI and PostgreSQL in Docker.",
    )
    unrelated = _make_offer(
        "unrelated",
        title="Junior SAP ABAP Consultant",
        skills=["SAP", "ABAP", "HANA"],
        level="JUNIOR",
        description="Maintain legacy SAP ERP modules.",
    )
    cv = _make_cv(
        "Senior Python Backend Engineer with 7 years of experience in Python, FastAPI, PostgreSQL, and Docker.",
        headline="Senior Python Backend Engineer",
        skills=["Python", "FastAPI", "PostgreSQL", "Docker"],
    )
    ranked = score_best_for_you(
        offers=[unrelated, matching],
        cv=cv,
        user_lat=50.0647,
        user_lon=19.9450,
    )
    assert ranked[0].offer.id == "match"
    assert (ranked[0].match_score or 0) > (ranked[1].match_score or 0)


def test_recommendation_skill_aliasing_matches_common_abbreviations() -> None:
    offer = _make_offer(
        "alias_offer",
        title="Platform Engineer",
        skills=["Kubernetes", "PostgreSQL", "TypeScript"],
        description="Kubernetes clusters, PostgreSQL databases, TypeScript tooling.",
    )
    cv_abbreviated = _make_cv("Platform Engineer experienced with k8s, postgres, and ts.")
    cv_unrelated = _make_cv("Graphic Designer working with Photoshop and Illustrator.")

    score_alias = score_best_for_you([offer], cv=cv_abbreviated, user_lat=50.06, user_lon=19.94)[0].match_score or 0
    score_unrelated = score_best_for_you([offer], cv=cv_unrelated, user_lat=50.06, user_lon=19.94)[0].match_score or 0
    assert score_alias > score_unrelated


def test_recommendation_around_you_orders_by_distance() -> None:
    krakow = _make_offer("krk", title="Dev", lat=50.0647, lon=19.9450)
    warsaw = _make_offer("waw", title="Dev", lat=52.2297, lon=21.0122)
    gdansk = _make_offer("gda", title="Dev", lat=54.3520, lon=18.6466)

    ordered = rank_around_you([gdansk, warsaw, krakow], user_lat=50.0647, user_lon=19.9450)
    assert [o.id for o in ordered] == ["krk", "waw", "gda"]


def test_recommendation_is_deterministic_across_repeated_calls() -> None:
    offers = [
        _make_offer("a", title="Python Engineer", skills=["Python", "Docker"], lat=50.06, lon=19.94),
        _make_offer("b", title="Java Engineer", skills=["Java", "Spring"], lat=52.23, lon=21.01),
        _make_offer("c", title="DevOps Engineer", skills=["Kubernetes", "Linux"], lat=51.11, lon=17.04),
    ]
    cv = _make_cv("Backend developer using Python, Docker, and Linux.")
    first = [(r.offer.id, r.match_score) for r in score_best_for_you(offers, cv=cv, user_lat=50.06, user_lon=19.94)]
    second = [(r.offer.id, r.match_score) for r in score_best_for_you(offers, cv=cv, user_lat=50.06, user_lon=19.94)]
    assert first == second


# ---------------------------------------------------------------------------
# 4. Scraper parser resilience against malformed external API payloads
# ---------------------------------------------------------------------------


@pytest.mark.parametrize(
    "bad_payload",
    [
        {},
        {"title": "", "slug": ""},
        {"title": "Engineer"},  # missing slug and guid
        {"slug": "only-slug"},  # missing title
    ],
)
def test_scraper_rejects_incomplete_items_cleanly(bad_payload: dict) -> None:
    scraper = JustJoinItScraper()
    assert scraper._parse_item(bad_payload) is None


def test_scraper_handles_corrupted_salaries_coords_and_skills_without_crashing() -> None:
    scraper = JustJoinItScraper()
    messy_item = {
        "title": "Cloud Engineer",
        "slug": "acme-cloud-engineer",
        "companyName": None,
        "experienceLevel": "unknown_tier",
        "workingTime": None,
        "employmentTypes": [
            {"type": "b2b", "from": "not-a-number", "to": math.nan, "currency": "PLN", "unit": "month"},
            {"type": "permanent", "from": math.inf, "to": None, "currency": None},
        ],
        "requiredSkills": ["not-a-dict", {"name": "", "level": 5}, {"name": "AWS", "level": 4}],
        "latitude": "invalid",
        "longitude": None,
    }
    parsed = scraper._parse_item(messy_item)
    assert parsed is not None
    assert parsed.title == "Cloud Engineer"
    assert parsed.external_url.endswith("acme-cloud-engineer")
    assert parsed.salary_range == "Undisclosed"
    assert "AWS" in parsed.skills
    assert -90.0 <= parsed.latitude <= 90.0
    assert -180.0 <= parsed.longitude <= 180.0

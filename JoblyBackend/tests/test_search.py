"""Specification-driven unit and HTTP E2E tests for the job search engine."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
import json

import httpx
import pytest

from app.models import CvModel, JobOfferModel
from app.schemas import CvStructuredData
from app.services import search_service


def _offer(
    offer_id: str,
    title: str,
    skills: list[str],
    company: str = "Acme Tech",
    level: str = "MID",
    contract_type: str = "B2B",
    work_schedule: str = "Full-Time",
    salary_range: str = "15 000 - 20 000",
    salary_unit: str = "PLN/month",
    description: str = "",
    lat: float | None = 50.0647,
    lon: float | None = 19.9450,
    created_at: datetime | None = None,
) -> JobOfferModel:
    return JobOfferModel(
        id=offer_id,
        initial=company[:1].upper() or "A",
        title=title,
        salary_range=salary_range,
        salary_unit=salary_unit,
        skills_json=json.dumps(skills),
        company_name=company,
        level=level,
        contract_type=contract_type,
        work_schedule=work_schedule,
        accent="YELLOW",
        description=description,
        source="justjoin.it",
        external_url=f"https://justjoin.it/job-offer/{offer_id}",
        latitude=lat,
        longitude=lon,
        created_at=created_at or datetime(2026, 11, 20, 12, 0, tzinfo=timezone.utc),
    )


def _cv(skills: list[str], headline: str = "Senior Backend Engineer") -> CvModel:
    structured = CvStructuredData(headline=headline, skills=skills, summary=f"{headline} {' '.join(skills)}")
    return CvModel(
        id="cv_search_test",
        user_id="default_user",
        file_name="cv.pdf",
        updated_label="Today",
        file_path="cv.pdf",
        raw_text=structured.summary,
        structured_json=structured.model_dump_json(),
    )


# ---------------------------------------------------------------------------
# Unit tests: search_service
# ---------------------------------------------------------------------------


def test_search_matches_title_skills_company_and_description_with_relevance_ranking() -> None:
    title_hit = _offer("o_title", "Senior Python Developer", ["Python", "FastAPI"], company="CloudSoft")
    desc_hit = _offer(
        "o_desc",
        "Backend Engineer",
        ["Go", "Docker"],
        company="OtherCorp",
        description="Workplace: Remote (Warszawa) | Experience with python scripting helpful.",
    )
    company_hit = _offer("o_comp", "Product Manager", ["Agile"], company="Pythonic Labs")
    miss = _offer("o_miss", "Java Architect", ["Java", "Spring"], company="EnterpriseCo")

    total, results = search_service.search_offers(
        offers=[miss, desc_hit, company_hit, title_hit],
        cv=_cv(["Python", "FastAPI"]),
        query="python",
    )
    assert total == 3
    ids = [r.offer.id for r in results]
    assert "o_miss" not in ids
    assert ids[0] == "o_title"


def test_search_multi_word_and_semantics() -> None:
    both = _offer(
        "both",
        "Senior Python Engineer",
        ["Python", "Docker"],
        description="Workplace: Hybrid (Kraków, Pawia 1)",
    )
    only_python = _offer(
        "only_py",
        "Senior Python Engineer",
        ["Python"],
        description="Workplace: Hybrid (Gdańsk, Grunwaldzka 1)",
    )
    total, results = search_service.search_offers(
        offers=[only_python, both],
        cv=None,
        query="python krakow",
    )
    assert total == 1
    assert results[0].offer.id == "both"


@pytest.mark.parametrize(
    "query,description,should_match",
    [
        ("krakow", "Workplace: Hybrid (Kraków, ul. Piękna)", True),
        ("wroclaw", "Workplace: Remote (Wrocław, Rynek 1)", True),
        ("warsaw", "Workplace: Office (Warszawa, Centrum)", True),
        ("warszawa", "Workplace: Office (Warsaw, Center)", True),
        ("lodz", "Workplace: Hybrid (Łódź, Piotrkowska)", True),
        ("gdansk", "Workplace: Hybrid (Poznań, Centrum)", False),
    ],
)
def test_search_diacritic_folding_and_city_aliasing(
    query: str, description: str, should_match: bool
) -> None:
    offer = _offer("geo", "Software Engineer", ["Python"], description=description)
    total, _ = search_service.search_offers([offer], cv=None, query=query)
    assert (total == 1) is should_match


def test_search_tech_aliases_and_typo_tolerance() -> None:
    k8s_offer = _offer("k8s", "Platform Engineer", ["Kubernetes", "Docker"])
    ts_offer = _offer("ts", "Frontend Developer", ["TypeScript", "React"])
    py_offer = _offer("py", "Python Developer", ["Python", "Django"])

    # Aliases: k8s -> Kubernetes, ts -> TypeScript
    total_k8s, res_k8s = search_service.search_offers([k8s_offer, ts_offer], cv=None, query="k8s")
    assert total_k8s == 1 and res_k8s[0].offer.id == "k8s"

    total_ts, res_ts = search_service.search_offers([k8s_offer, ts_offer], cv=None, query="ts")
    assert total_ts == 1 and res_ts[0].offer.id == "ts"

    # Typos: "pyhton" -> Python, "kubernets" -> Kubernetes
    total_py_typo, res_py_typo = search_service.search_offers([k8s_offer, py_offer], cv=None, query="pyhton")
    assert total_py_typo == 1 and res_py_typo[0].offer.id == "py"

    total_k8s_typo, res_k8s_typo = search_service.search_offers([k8s_offer, py_offer], cv=None, query="kubernets")
    assert total_k8s_typo == 1 and res_k8s_typo[0].offer.id == "k8s"


def test_search_structured_facets_and_combined_filtering() -> None:
    remote_senior = _offer(
        "rem_sr",
        "Senior Backend Engineer",
        ["Python"],
        level="SENIOR",
        contract_type="B2B",
        salary_range="24 000 - 30 000",
        description="Workplace: Remote (Warszawa)",
    )
    hybrid_mid = _offer(
        "hyb_mid",
        "Mid Python Developer",
        ["Python"],
        level="MID",
        contract_type="UoP",
        salary_range="14 000 - 18 000",
        description="Workplace: Hybrid (Kraków)",
    )
    office_undisclosed = _offer(
        "off_und",
        "Junior Developer",
        ["Python"],
        level="JUNIOR",
        contract_type="UoP",
        salary_range="Undisclosed",
        description="Workplace: Office (Wrocław)",
    )
    pool = [remote_senior, hybrid_mid, office_undisclosed]

    assert [r.offer.id for r in search_service.search_offers(pool, None, level="SENIOR")[1]] == ["rem_sr"]
    assert [r.offer.id for r in search_service.search_offers(pool, None, contract_type="b2b")[1]] == ["rem_sr"]
    assert [r.offer.id for r in search_service.search_offers(pool, None, work_mode="remote")[1]] == ["rem_sr"]
    assert [r.offer.id for r in search_service.search_offers(pool, None, work_mode="hybrid")[1]] == ["hyb_mid"]
    assert [r.offer.id for r in search_service.search_offers(pool, None, work_mode="office")[1]] == ["off_und"]
    assert [r.offer.id for r in search_service.search_offers(pool, None, city="Krakow")[1]] == ["hyb_mid"]
    assert [r.offer.id for r in search_service.search_offers(pool, None, min_salary=20000)[1]] == ["rem_sr"]
    assert [
        r.offer.id
        for r in search_service.search_offers(
            pool,
            None,
            query="python",
            level="MID",
            work_mode="hybrid",
            city="Krakow",
            min_salary=15000,
        )[1]
    ] == ["hyb_mid"]


def test_search_sorting_modes_and_pagination() -> None:
    t0 = datetime(2026, 11, 1, 12, 0, tzinfo=timezone.utc)
    krakow_low_new = _offer(
        "krk_low_new",
        "Junior Go Dev",
        ["Go"],
        level="JUNIOR",
        salary_range="8 000 - 10 000",
        description="Workplace: Office (Kraków)",
        lat=50.0647,
        lon=19.9450,
        created_at=t0 + timedelta(days=5),
    )
    warsaw_high_old = _offer(
        "waw_high_old",
        "Principal Architect",
        ["Kubernetes", "Python"],
        level="SENIOR",
        salary_range="35 000 - 45 000",
        description="Workplace: Remote (Warszawa)",
        lat=52.2297,
        lon=21.0122,
        created_at=t0,
    )
    gdansk_mid = _offer(
        "gda_mid",
        "Senior Python Engineer",
        ["Python", "FastAPI", "Kubernetes"],
        level="SENIOR",
        salary_range="20 000 - 25 000",
        description="Workplace: Hybrid (Gdańsk)",
        lat=54.3520,
        lon=18.6466,
        created_at=t0 + timedelta(days=2),
    )
    pool = [krakow_low_new, warsaw_high_old, gdansk_mid]
    cv = _cv(["Python", "FastAPI", "Kubernetes"], headline="Senior Python Engineer")

    # salary_desc
    _, by_sal = search_service.search_offers(pool, cv, sort="salary_desc")
    assert [r.offer.id for r in by_sal] == ["waw_high_old", "gda_mid", "krk_low_new"]

    # nearest (from Krakow)
    _, by_near = search_service.search_offers(pool, cv, sort="nearest", user_lat=50.0647, user_lon=19.9450)
    assert [r.offer.id for r in by_near] == ["krk_low_new", "waw_high_old", "gda_mid"]
    assert any("km away" in r for r in by_near[0].match_reasons)

    # newest
    _, by_new = search_service.search_offers(pool, cv, sort="newest")
    assert [r.offer.id for r in by_new] == ["krk_low_new", "gda_mid", "waw_high_old"]

    # best_match
    _, by_match = search_service.search_offers(pool, cv, sort="best_match")
    assert by_match[0].offer.id == "gda_mid"
    assert (by_match[0].match_score or 0) >= (by_match[-1].match_score or 0)

    # pagination preserves total
    total, page2 = search_service.search_offers(pool, cv, sort="salary_desc", limit=1, offset=1)
    assert total == 3
    assert len(page2) == 1
    assert page2[0].offer.id == "gda_mid"


# ---------------------------------------------------------------------------
# HTTP E2E tests: GET /offers/search
# ---------------------------------------------------------------------------


@pytest.mark.asyncio
async def test_http_search_offers_endpoints_filters_sorts_and_edge_cases(
    client: httpx.AsyncClient,
) -> None:
    # Empty query returns all seeded offers with personalized match_score
    all_resp = await client.get("/offers/search")
    assert all_resp.status_code == 200
    all_body = all_resp.json()
    assert all_body["total"] == len(all_body["offers"])
    assert all_body["total"] >= 1
    assert all(o["match_score"] is not None for o in all_body["offers"])

    # Query + city with Polish diacritic folding + sort
    krk_resp = await client.get(
        "/offers/search",
        params={"q": "k8s", "city": "krakow", "sort": "best_match", "limit": 2, "offset": 0},
    )
    assert krk_resp.status_code == 200
    krk_body = krk_resp.json()
    assert krk_body["query"] == "k8s"
    assert krk_body["total"] >= 1
    assert len(krk_body["offers"]) <= 2

    # Non-matching query and adversarial string both return 200 OK cleanly
    empty_resp = await client.get(
        "/offers/search",
        params={"q": "nonexistent_quantum_blockchain_999'; DROP TABLE job_offers;--"},
    )
    assert empty_resp.status_code == 200
    assert empty_resp.json()["total"] == 0
    assert empty_resp.json()["offers"] == []

    # Invalid sort degrades gracefully to relevance
    weird_sort = await client.get("/offers/search", params={"sort": "unknown_mode"})
    assert weird_sort.status_code == 200
    assert weird_sort.json()["total"] == all_body["total"]

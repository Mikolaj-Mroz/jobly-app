from io import BytesIO
import json
from pathlib import Path
from fpdf import FPDF
import httpx
from pypdf import PdfReader
import pytest
from app.config import Settings
from app.main import create_app
from app.services.llm_service import OpenAiCompatibleClient
from app.services.scraper_service import (
    ArbeitnowScraper,
    BaseJobScraper,
    JobIngestionService,
    JustJoinItScraper,
    ScrapedOffer,
)


@pytest.mark.asyncio
async def test_assistant_tool_call_edits_pdf_and_returns_updated_cv(tmp_path: Path):
    turn_counter = {"count": 0}

    def llm_handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/models"):
            return httpx.Response(
                200,
                json={
                    "data": [
                        {"id": "text-embedding-nomic-embed-text-v1.5"},
                        {"id": "prism-ml/bonsai-27b"},
                    ]
                },
            )
        if request.url.path.endswith("/embeddings"):
            return httpx.Response(200, json={"data": [{"embedding": [0.1, 0.2, 0.3]}]})

        if request.url.path.endswith("/chat/completions"):
            turn_counter["count"] += 1
            if turn_counter["count"] == 1:
                return httpx.Response(
                    200,
                    json={
                        "choices": [
                            {
                                "message": {
                                    "role": "assistant",
                                    "content": "",
                                    "tool_calls": [
                                        {
                                            "id": "call_cv_1",
                                            "type": "function",
                                            "function": {
                                                "name": "update_cv",
                                                "arguments": json.dumps(
                                                    {
                                                        "headline": "Staff Kubernetes QA Architect",
                                                        "summary": "Specialized in automated Kubernetes chaos testing and ELK pipelines.",
                                                        "skills": ["Kubernetes", "ELK", "Chaos Mesh", "Python"],
                                                    }
                                                ),
                                            },
                                        }
                                    ],
                                }
                            }
                        ]
                    },
                )
            return httpx.Response(
                200,
                json={
                    "choices": [
                        {
                            "message": {
                                "role": "assistant",
                                "content": "I updated My-new-cv.pdf to highlight your Staff Kubernetes QA Architect skills.",
                            }
                        }
                    ]
                },
            )
        return httpx.Response(404)

    mock_http = httpx.AsyncClient(transport=httpx.MockTransport(llm_handler))
    settings = Settings(
        database_url=f"sqlite+aiosqlite:///{(tmp_path / 'asst.db').as_posix()}",
        storage_dir=str(tmp_path / "storage"),
        enable_scheduler=False,
    )
    llm_client = OpenAiCompatibleClient(settings, http_client=mock_http)
    app = create_app(settings_override=settings, llm_client_override=llm_client)

    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            resp = await client.post(
                "/assistant/chat",
                json={"message": "Please tailor my CV for Kubernetes QA", "cv_id": "cv_1"},
            )
            assert resp.status_code == 200
            payload = resp.json()
            assert payload["model"] == "prism-ml/bonsai-27b"
            assert "My-new-cv.pdf" in payload["reply"]
            assert payload["updated_cv"]["id"] == "cv_1"

            # Verify the PDF file on disk was actually regenerated with the AI's edits
            dl = await client.get("/user/cvs/cv_1/download")
            assert dl.status_code == 200
            pdf_text = "\n".join(
                p.extract_text() or "" for p in PdfReader(BytesIO(dl.content)).pages
            )
            assert "Staff Kubernetes QA Architect" in pdf_text
            assert "Chaos Mesh" in pdf_text

    await mock_http.aclose()


@pytest.mark.asyncio
async def test_scraper_ingestion_deduplicates_external_offers(tmp_path: Path):
    def scraper_handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "data": [
                    {
                        "slug": "fastapi-dev-1",
                        "url": "https://example.com/jobs/fastapi-dev-1",
                        "title": "Senior FastAPI Engineer",
                        "company_name": "Acme AI",
                        "tags": ["Python", "FastAPI", "Postgres"],
                        "description": "<p>Build scalable recommendation pipelines.</p>",
                        "remote": True,
                    }
                ]
            },
        )

    mock_http = httpx.AsyncClient(transport=httpx.MockTransport(scraper_handler))

    class StubArbeitnow(ArbeitnowScraper):
        async def fetch(self, client: httpx.AsyncClient):
            return await super().fetch(mock_http)

    settings = Settings(
        database_url=f"sqlite+aiosqlite:///{(tmp_path / 'scrape.db').as_posix()}",
        storage_dir=str(tmp_path / "storage"),
        enable_scheduler=False,
    )
    ingestion = JobIngestionService(scrapers=[StubArbeitnow()])
    app = create_app(settings_override=settings, ingestion_service_override=ingestion)

    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            first_scrape = await client.post("/offers/scrape")
            assert first_scrape.status_code == 200
            assert first_scrape.json() == {
                "sources_checked": 1,
                "fetched_count": 1,
                "inserted_count": 1,
                "notifications_created": 1,
            }

            # Running again should deduplicate
            second_scrape = await client.post("/offers/scrape")
            assert second_scrape.status_code == 200
            assert second_scrape.json()["inserted_count"] == 0

            offers = await client.get("/offers/around-you", params={"query": "FastAPI"})
            assert len(offers.json()["offers"]) == 1
            assert offers.json()["offers"][0]["title"] == "Senior FastAPI Engineer"

    await mock_http.aclose()


@pytest.mark.asyncio
async def test_justjoin_scraper_pagination_salary_skills_coords_and_dedup(tmp_path: Path):
    requests_seen: list[tuple[str | None, str | None]] = []

    def justjoin_handler(request: httpx.Request) -> httpx.Response:
        cursor = request.url.params.get("from")
        items_count = request.url.params.get("itemsCount")
        requests_seen.append((cursor, items_count))

        if cursor in (None, "0"):
            return httpx.Response(
                200,
                json={
                    "data": [
                        {
                            "guid": "jj-guid-1",
                            "slug": "acme-senior-python-engineer",
                            "title": "Senior Python Backend Engineer",
                            "companyName": "Acme Polska",
                            "workplaceType": "hybrid",
                            "workingTime": "full_time",
                            "experienceLevel": "senior",
                            "category": {"key": "python"},
                            "city": "Krakow",
                            "street": "Lubicz 17",
                            "latitude": 50.0655,
                            "longitude": 19.9560,
                            "employmentTypes": [
                                {
                                    "currencySource": "conversion",
                                    "currency": "EUR",
                                    "from": 4200,
                                    "to": 5600,
                                    "type": "b2b",
                                    "unit": "Month",
                                },
                                {
                                    "currencySource": "original",
                                    "currency": "PLN",
                                    "from": 18000,
                                    "to": 24000,
                                    "type": "b2b",
                                    "unit": "Month",
                                },
                            ],
                            "requiredSkills": [
                                {"skillId": 1, "name": "Docker", "level": 2},
                                {"skillId": 2, "name": "Python", "level": 5},
                                {"skillId": 3, "name": "Git", "level": 1},
                                {"skillId": 4, "name": "FastAPI", "level": 4},
                                {"skillId": 5, "name": "PostgreSQL", "level": 3},
                            ],
                            "niceToHaveSkills": [{"name": "Kubernetes"}],
                            "languages": [{"code": "en", "level": "B2"}],
                        },
                        {
                            "guid": "jj-guid-2",
                            "slug": "beta-uop-qa-engineer",
                            "title": "Mid QA Engineer Undisclosed",
                            "companyName": "Beta Tech",
                            "workplaceType": "office",
                            "workingTime": "full_time",
                            "experienceLevel": "mid",
                            "category": {"key": "testing"},
                            "city": "Warszawa",
                            "street": "Marszalkowska 1",
                            "latitude": None,
                            "longitude": None,
                            "locations": [{"latitude": 52.2297, "longitude": 21.0122}],
                            "employmentTypes": [
                                {
                                    "currencySource": "original",
                                    "currency": "PLN",
                                    "from": None,
                                    "to": None,
                                    "type": "permanent",
                                    "unit": "Month",
                                }
                            ],
                            "requiredSkills": [
                                {"skillId": 10, "name": "Pytest", "level": 4},
                                {"skillId": 11, "name": "Playwright", "level": 5},
                            ],
                        },
                    ],
                    "meta": {"next": {"cursor": 2}},
                },
            )

        if cursor == "2":
            return httpx.Response(
                200,
                json={
                    "data": [
                        {
                            "guid": "jj-guid-3",
                            "slug": "gamma-usd-data-engineer",
                            "title": "Remote Data Platform Engineer",
                            "companyName": "Gamma Cloud",
                            "workplaceType": "remote",
                            "workingTime": "part_time",
                            "experienceLevel": "junior",
                            "city": "Gdansk",
                            "latitude": 54.3520,
                            "longitude": 18.6466,
                            "employmentTypes": [
                                {
                                    "currencySource": "original",
                                    "currency": "USD",
                                    "from": 5000,
                                    "to": None,
                                    "type": "mandate_contract",
                                    "unit": "Month",
                                }
                            ],
                            "requiredSkills": [{"name": "Spark", "level": 3}],
                        }
                    ],
                    "meta": {"next": {"cursor": None}},
                },
            )

        return httpx.Response(404)

    mock_http = httpx.AsyncClient(transport=httpx.MockTransport(justjoin_handler))

    class StubJustJoin(JustJoinItScraper):
        async def fetch(self, client: httpx.AsyncClient):
            return await super().fetch(mock_http)

    settings = Settings(
        database_url=f"sqlite+aiosqlite:///{(tmp_path / 'justjoin.db').as_posix()}",
        storage_dir=str(tmp_path / "storage"),
        enable_scheduler=False,
    )
    ingestion = JobIngestionService(
        scrapers=[
            StubJustJoin(items_per_page=2, max_pages=3, page_delay_seconds=0.0),
            ArbeitnowScraper(),
        ]
    )
    app = create_app(settings_override=settings, ingestion_service_override=ingestion)

    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            first_scrape = await client.post(
                "/offers/scrape", params={"source": "justjoin.it"}
            )
            assert first_scrape.status_code == 200
            assert first_scrape.json() == {
                "sources_checked": 1,
                "fetched_count": 3,
                "inserted_count": 3,
                "notifications_created": 2,
            }
            assert requests_seen == [("0", "2"), ("2", "2")]

            # Unknown source returns HTTP 400
            bad_source = await client.post("/offers/scrape", params={"source": "unknown"})
            assert bad_source.status_code == 400

            # Repeat scrape deduplicates existing external URLs
            second_scrape = await client.post(
                "/offers/scrape", params={"source": "justjoin.it"}
            )
            assert second_scrape.status_code == 200
            assert second_scrape.json()["fetched_count"] == 3
            assert second_scrape.json()["inserted_count"] == 0

            # Offer 1: Krakow coordinates, original PLN salary, top-4 skills sorted desc by level
            krk_resp = await client.get(
                "/offers/around-you",
                params={"lat": 50.0655, "lon": 19.9560, "radius_km": 2.0, "query": "Senior Python"},
            )
            assert krk_resp.status_code == 200
            krk_offers = krk_resp.json()["offers"]
            assert len(krk_offers) == 1
            offer1 = krk_offers[0]
            assert offer1["company_name"] == "Acme Polska"
            assert offer1["salary_range"] == "18 000 - 24 000"
            assert offer1["salary_unit"] == "PLN/month"
            assert offer1["contract_type"] == "B2B"
            assert offer1["level"] == "SENIOR"
            assert offer1["work_schedule"] == "Full-Time"
            assert offer1["skills"] == ["Python", "FastAPI", "PostgreSQL", "Docker"]
            assert "https://justjoin.it/job-offer/acme-senior-python-engineer" in offer1["description"]

            # Offer 2: locations[0] coordinate fallback + Undisclosed UoP salary
            waw_resp = await client.get(
                "/offers/around-you",
                params={"lat": 52.2297, "lon": 21.0122, "radius_km": 2.0, "query": "Undisclosed"},
            )
            assert waw_resp.status_code == 200
            waw_offers = waw_resp.json()["offers"]
            assert len(waw_offers) == 1
            offer2 = waw_offers[0]
            assert offer2["salary_range"] == "Undisclosed"
            assert offer2["salary_unit"] == ""
            assert offer2["contract_type"] == "UoP"
            assert offer2["level"] == "MID"
            assert offer2["skills"] == ["Playwright", "Pytest"]

            # Offer 3 (page 2): lower-bound-only USD salary + part_time + mandate_contract
            gdn_resp = await client.get(
                "/offers/around-you",
                params={"lat": 54.3520, "lon": 18.6466, "radius_km": 2.0, "query": "Data Platform"},
            )
            assert gdn_resp.status_code == 200
            gdn_offers = gdn_resp.json()["offers"]
            assert len(gdn_offers) == 1
            offer3 = gdn_offers[0]
            assert offer3["salary_range"] == "From 5 000"
            assert offer3["salary_unit"] == "USD/month"
            assert offer3["contract_type"] == "UZ"
            assert offer3["level"] == "JUNIOR"
            assert offer3["work_schedule"] == "Part-Time"

    await mock_http.aclose()


@pytest.mark.asyncio
async def test_scrape_status_reports_scheduler_and_last_run(tmp_path: Path):
    assert "justjoin.it" in JobIngestionService().source_names

    class StubJustJoinEmpty(BaseJobScraper):
        source_name = "justjoin.it"

        async def fetch(self, client: httpx.AsyncClient) -> list[ScrapedOffer]:
            return [
                ScrapedOffer(
                    external_url="https://justjoin.it/job-offer/stub-status-role",
                    title="Stub Scheduler QA Engineer",
                    company_name="Scheduler Corp",
                    salary_range="15 000 - 20 000",
                    salary_unit="PLN/month",
                    skills=["Python", "Pytest"],
                    level="MID",
                    contract_type="B2B",
                    work_schedule="Full-Time",
                    description="Stubbed offer for scheduler status test",
                    source=self.source_name,
                )
            ]

    settings = Settings(
        database_url=f"sqlite+aiosqlite:///{(tmp_path / 'status.db').as_posix()}",
        storage_dir=str(tmp_path / "storage"),
        enable_scheduler=True,
        scrape_on_startup=False,
        scrape_interval_minutes=30,
    )
    ingestion = JobIngestionService(scrapers=[StubJustJoinEmpty()])
    app = create_app(settings_override=settings, ingestion_service_override=ingestion)

    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            initial_status = await client.get("/offers/scrape/status")
            assert initial_status.status_code == 200
            initial_json = initial_status.json()
            assert initial_json["scheduler_enabled"] is True
            assert initial_json["interval_minutes"] == 30
            assert initial_json["scrape_on_startup"] is False
            assert initial_json["next_run_at"] is not None
            assert initial_json["last_run_at"] is None
            assert initial_json["last_summary"] is None
            assert "justjoin.it" in initial_json["sources"]

            scrape_resp = await client.post("/offers/scrape")
            assert scrape_resp.status_code == 200

            updated_status = await client.get("/offers/scrape/status")
            assert updated_status.status_code == 200
            updated_json = updated_status.json()
            assert updated_json["last_run_at"] is not None
            assert updated_json["last_summary"] == scrape_resp.json()


@pytest.mark.asyncio
async def test_ai_really_edits_uploaded_pdf_preserving_sections_and_unicode(tmp_path: Path):
    turn_counter = {"count": 0}
    captured_tool_output: list[str] = []

    def llm_handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/models"):
            return httpx.Response(200, json={"data": [{"id": "qwen3-coder-30b-a3b-instruct"}]})
        if request.url.path.endswith("/chat/completions"):
            turn_counter["count"] += 1
            body = json.loads(request.content.decode("utf-8"))
            if turn_counter["count"] == 1:
                return httpx.Response(
                    200,
                    json={
                        "choices": [
                            {
                                "message": {
                                    "role": "assistant",
                                    "content": "",
                                    "tool_calls": [
                                        {
                                            "id": "call_edit_real_pdf",
                                            "type": "function",
                                            "function": {
                                                "name": "update_cv",
                                                "arguments": json.dumps(
                                                    {
                                                        "add_skills": ["Docker", "PostgreSQL"],
                                                        "summary": "Doświadczony inżynier backendowy w Krakowie.",
                                                        "education": "Mgr inż. Politechnika Krakowska — Informatyka",
                                                    }
                                                ),
                                            },
                                        }
                                    ],
                                }
                            }
                        ]
                    },
                )
            for msg in body.get("messages", []):
                if msg.get("role") == "tool":
                    captured_tool_output.append(msg.get("content", ""))
            return httpx.Response(
                200,
                json={
                    "choices": [
                        {
                            "message": {
                                "role": "assistant",
                                "content": "Zaktualizowałem Twoje CV PDF: dodano Docker i PostgreSQL.",
                            }
                        }
                    ]
                },
            )
        return httpx.Response(404)

    # Create a real uploaded PDF representing a different person/stack (no QA defaults)
    pdf = FPDF()
    pdf.add_page()
    font_path = Path("C:/Windows/Fonts/arial.ttf")
    if font_path.exists():
        pdf.add_font("ArialUni", "", str(font_path))
        pdf.set_font("ArialUni", size=11)
    else:
        pdf.set_font("Helvetica", size=11)
    for line in [
        "Jan Kowalski",
        "Backend Python Developer",
        "jan.kowalski@dev.pl",
        "Umiejętności",
        "Python, FastAPI",
        "Wykształcenie",
        "Politechnika Krakowska",
    ]:
        pdf.cell(0, 8, line, new_x="LMARGIN", new_y="NEXT")
    uploaded_bytes = bytes(pdf.output())

    mock_http = httpx.AsyncClient(transport=httpx.MockTransport(llm_handler))
    settings = Settings(
        database_url=f"sqlite+aiosqlite:///{(tmp_path / 'ai_pdf_edit.db').as_posix()}",
        storage_dir=str(tmp_path / "storage"),
        enable_scheduler=False,
    )
    llm_client = OpenAiCompatibleClient(settings, http_client=mock_http)
    app = create_app(settings_override=settings, llm_client_override=llm_client)

    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app=app), base_url="http://test"
        ) as client:
            upload_resp = await client.post(
                "/user/cvs/upload",
                files={"file": ("Jan-Kowalski-CV.pdf", uploaded_bytes, "application/pdf")},
            )
            assert upload_resp.status_code == 200
            uploaded_cv_id = upload_resp.json()["id"]

            chat_resp = await client.post(
                "/assistant/chat",
                json={
                    "message": "Add Docker and PostgreSQL to my skills and update my Polish summary.",
                    "cv_id": uploaded_cv_id,
                },
            )
            assert chat_resp.status_code == 200
            chat_json = chat_resp.json()
            assert chat_json["updated_cv"]["id"] == uploaded_cv_id
            assert chat_json["updated_cv"]["updated_label"] == "Edited by AI"
            assert chat_json["updated_cv"]["page_count"] >= 1
            assert chat_json["updated_cv"]["file_size_bytes"] > 0

            tool_payload = json.loads(captured_tool_output[-1])
            assert tool_payload["pdf_verified_pages"] >= 1
            assert "skills" in tool_payload["changed_fields"]

            dl_resp = await client.get(f"/user/cvs/{uploaded_cv_id}/download")
            assert dl_resp.status_code == 200
            assert dl_resp.headers.get("cache-control") == "no-store"
            pdf_text = "\n".join(
                p.extract_text() or "" for p in PdfReader(BytesIO(dl_resp.content)).pages
            )
            assert "Jan Kowalski" in pdf_text
            assert "Backend Python Developer" in pdf_text
            assert "FastAPI" in pdf_text
            assert "Docker" in pdf_text
            assert "PostgreSQL" in pdf_text
            assert "Politechnika Krakowska" in pdf_text
            if font_path.exists():
                assert "Doświadczony" in pdf_text
            assert "Quality Assurance" not in pdf_text

    await mock_http.aclose()

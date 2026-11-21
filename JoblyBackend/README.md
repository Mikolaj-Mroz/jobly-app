# JoblyBackend

FastAPI + Async SQLAlchemy backend powering the **Jobly** Android app.

---

## Core Modules

1. **Offers, Recommendations & Search (`/offers`):**
   - **Next to you (`/offers/around-you`):** Haversine geo-ranked offers (`None`-coordinate safe) with radius and text filtering.
   - **Best for you (`/offers/best-for-you`):** Multi-signal hybrid recommendation engine (`recommendation_service.py`) combining skill overlap + tech aliases, corpus TF-IDF / embedding cosine similarity, title & seniority alignment, geo/remote score, and application-history novelty.
   - **Search Engine (`/offers/search`):** Full-text + faceted search (`search_service.py`) with multi-word `AND` semantics, Polish diacritic folding (`Kraków` <-> `krakow`, `Warsaw` <-> `Warszawa`), fuzzy typo tolerance (`>= 0.80`), filters (`level`, `contract_type`, `work_mode`, `city`, `min_salary`), 5 sort modes (`relevance`, `best_match`, `salary_desc`, `nearest`, `newest`), and pagination.
   - **Job Ingestion (`APScheduler` + `JustJoinItScraper`):** Periodic scraping prioritizing JustJoin.it alongside Arbeitnow and RemoteOK (`POST /offers/scrape`, `GET /offers/scrape/status`).
2. **AI Assistant (`/assistant`):**
   - OpenAI-compatible proxy (`LLM_BASE_URL`, defaulting to `http://127.0.0.1:1234/v1`) supporting tool calling (`update_cv`, `search_job_offers`) to search offers and edit/regenerate CV `.pdf` files in place.
3. **Notifications & Daily Digest (`/notifications`):**
   - Notification list & idempotent read tracking plus an automated daily best-match digest job (`daily_best_offer_notification`, `POST /notifications/daily-digest`, `GET /notifications/digest/status`).
4. **User, Profile, Settings, Feedback & CVs (`/user`):**
   - Profile (`GET/PUT /user/profile`), persistent settings (`GET/PUT /user/settings`), feedback collection (`POST /user/feedback`), applications history (`GET /user/applications`), blank empty CV creation (`new-cv.pdf` containing only user contact header), PDF upload/validation/parsing (`pypdf`), structured CV editing, and Unicode PDF generation/download (`fpdf2`).
   - **Automatic CV Localization:** Extracts user city/coordinates from uploaded or edited CV PDF text (`sync_user_location_from_cv_text`) to sync user coordinates and `user_settings.city`, skipping cleanly if no recognized city is found.

---

## Local Setup (Windows / Python)

```powershell
cd JoblyBackend
python -m venv .venv
.\.venv\Scripts\pip.exe install -r requirements.txt
Copy-Item .env.example .env
.\.venv\Scripts\uvicorn.exe app.main:app --host 0.0.0.0 --port 8000 --reload
```

Interactive OpenAPI / Swagger UI: `http://localhost:8000/docs`

---

## Environment Variables (`.env`)

All settings are defined in [app/config.py](app/config.py) and templated in [.env.example](.env.example):

| Variable | Default | Description |
|---|---|---|
| `DATABASE_URL` | `sqlite+aiosqlite:///./jobly.db` | Async SQLAlchemy DB URL (or PostgreSQL `postgresql+asyncpg://...`) |
| `LLM_BASE_URL` | `http://127.0.0.1:1234/v1` | OpenAI-compatible base URL (LM Studio, vLLM, or cloud proxy) |
| `LLM_API_KEY` | `lm-studio` | Bearer API key sent to `LLM_BASE_URL` |
| `LLM_TIMEOUT_SECONDS` | `120.0` | HTTP timeout for LLM chat & embedding requests |
| `STORAGE_DIR` | `./storage` | Directory where generated and uploaded CV `.pdf` files are stored |
| `ENABLE_SCHEDULER` | `true` | Enables `APScheduler` background jobs |
| `SCRAPE_INTERVAL_MINUTES` | `60` | Interval for `scrape_job_offers` background job |
| `SCRAPE_ON_STARTUP` | `false` | Whether to trigger a scrape immediately on server startup |
| `ENABLE_DAILY_NOTIFICATION` | `true` | Enables the daily best-match notification job |
| `DAILY_NOTIFICATION_INTERVAL_HOURS` | `24` | Interval for `daily_best_offer_notification` |
| `JUSTJOIN_ITEMS_PER_PAGE` | `50` | Items fetched per JustJoin.it API page |
| `JUSTJOIN_MAX_PAGES` | `2` | Max JustJoin.it pages per scrape run |
| `MAX_CV_FILE_SIZE_BYTES` | `5242880` | Max PDF upload size (`5 MB`) |
| `MAX_CV_PAGES` | `10` | Max PDF page count |
| `MAX_CVS_PER_USER` | `10` | Max CVs stored per user |
| `DEFAULT_USER_ID` | `default_user` | Default single-user profile ID |
| `DEFAULT_USER_LAT` | `50.0647` | Fallback latitude (Kraków) |
| `DEFAULT_USER_LON` | `19.9450` | Fallback longitude (Kraków) |

> **Using a Cloud LLM (e.g. Gemini via OpenAI compatibility / OpenRouter / Google Cloud):** Point `LLM_BASE_URL` and `LLM_API_KEY` in `.env` to your cloud provider's OpenAI-compatible endpoint without changing backend code.

---

## API Endpoints Overview (25 Total)

- **System:** `GET /health`
- **Offers & Search:** `GET /offers/around-you`, `GET /offers/best-for-you`, `GET /offers/search`, `POST /offers/{offer_id}/apply`, `POST /offers/scrape`, `GET /offers/scrape/status`
- **Notifications:** `GET /notifications`, `PATCH /notifications/{notification_id}/read`, `POST /notifications/daily-digest`, `GET /notifications/digest/status`
- **User & CVs:** `GET /user/profile`, `PUT /user/profile`, `GET /user/settings`, `PUT /user/settings`, `POST /user/feedback`, `GET /user/applications`, `GET /user/cvs`, `POST /user/cvs`, `POST /user/cvs/upload`, `PUT /user/cvs/{cv_id}`, `DELETE /user/cvs/{cv_id}`, `GET /user/cvs/{cv_id}/download`
- **Assistant:** `GET /assistant/health`, `POST /assistant/chat`

Full request/response documentation is in [Jobly/docs/API_REFERENCE.md](../Jobly/docs/API_REFERENCE.md).

---

## Database Tables

Initialized automatically via `init_db` in `app/models.py`:
- `users`, `job_offers`, `cvs`, `applications`, `notifications`, `user_settings`, `feedback_messages`.

---

## Running Tests

Runs **64** unit, black-box specification, search engine, scraper, notification, PDF, and HTTP E2E tests:

```powershell
cd JoblyBackend
.\.venv\Scripts\pytest.exe -v
```

---

## Docker + PostgreSQL (`pgvector`)

```bash
docker compose up --build
```

## Connecting the Jobly Android App

In [NetworkConfig.kt](../Jobly/app/src/main/java/com/example/jobly/data/remote/NetworkConfig.kt):
- **Android Emulator (`10.0.2.2:8000`):** Automatically used on emulator builds (`offers`, `notifications`, `user`, `assistant`).
- **Physical Device (`127.0.0.1:8000`):** Connect via USB and run `adb reverse tcp:8000 tcp:8000`.

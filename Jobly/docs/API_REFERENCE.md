# Jobly Backend API Reference

Base URL:
- **Host / Local:** `http://127.0.0.1:8000`
- **Android Emulator:** `http://10.0.2.2:8000`
- **OpenAPI / Swagger UI:** `http://127.0.0.1:8000/docs`

All JSON endpoints follow `application/json`. Total endpoints exposed: **25**.

---

## 1. System

| Method | Path | Description |
|---|---|---|
| `GET` | `/health` | Returns `{"status": "ok"}` when the FastAPI app is healthy. |

---

## 2. Offers, Search & Scraping (`/offers`)

| Method | Path | Query / Body | Response Model |
|---|---|---|---|
| `GET` | `/offers/around-you` | Query: `query` (`""`), `lat`, `lon`, `radius_km` | `OffersResponseDto` |
| `GET` | `/offers/best-for-you` | Query: `query` (`""`), `cv_id` | `OffersResponseDto` |
| `GET` | `/offers/search` | Query: `q`, `level`, `contract_type`, `work_mode`, `city`, `min_salary`, `sort`, `cv_id`, `limit`, `offset` | `SearchOffersResponseDto` |
| `POST` | `/offers/{offer_id}/apply` | Body: `{"cv_id": "..."}` | `ApplyOfferResponseDto` |
| `POST` | `/offers/scrape` | Query: `source` (optional, e.g. `justjoin.it`) | `ScrapeSummaryDto` |
| `GET` | `/offers/scrape/status` | — | `ScrapeStatusDto` |

### `GET /offers/search` Details

Full-text and faceted search across all stored job offers with typo tolerance, Polish diacritic folding (`Kraków` <-> `krakow`, `Warsaw` <-> `Warszawa`), tech alias matching (`k8s`, `ts`, `py`, `postgres`), and personalized `match_score`.

**Query Parameters:**

| Parameter | Type | Default | Notes |
|---|---|---|---|
| `q` | `string` | `""` | Space-separated search terms (strict AND across terms). |
| `level` | `string` | `null` | `JUNIOR`, `MID`, `SENIOR`, `MANAGER`, `C-LEVEL` (case-insensitive). |
| `contract_type` | `string` | `null` | `B2B`, `UoP` (case-insensitive). |
| `work_mode` | `string` | `null` | `remote`, `hybrid`, or `office`. |
| `city` | `string` | `null` | Diacritic-insensitive city substring filter. |
| `min_salary` | `int` | `null` | Minimum monthly salary (`>= 0`); excludes `Undisclosed` salaries when `> 0`. |
| `sort` | `string` | `"relevance"` | `relevance`, `best_match`, `salary_desc`, `nearest`, or `newest`. |
| `cv_id` | `string` | `null` | Optional CV ID used to compute `match_score`; defaults to latest CV. |
| `limit` | `int` | `50` | Page size (`1..200`). |
| `offset` | `int` | `0` | Pagination offset (`>= 0`). |

**Example Response (`200 OK`):**

```json
{
  "query": "python",
  "total": 11,
  "offers": [
    {
      "id": "ext_4d774a2cf77b",
      "initial": "B",
      "title": "Senior GenAI Engineer - Python",
      "salary_range": "Undisclosed",
      "salary_unit": "",
      "skills": ["Python", "LLM", "Langgraph"],
      "company_name": "Billennium",
      "level": "SENIOR",
      "contract_type": "B2B",
      "work_schedule": "Full-Time",
      "accent": "PINK",
      "description": "Workplace: Remote (Warszawa, -) | Category: ai | ...",
      "external_url": "https://justjoin.it/job-offer/...",
      "match_score": 68,
      "match_reasons": ["Skills: Python", "Remote-friendly"]
    }
  ]
}
```

### `POST /offers/{offer_id}/apply`

Idempotent application endpoint:
- First application inserts a row in `applications` and returns `{"applied": true, "message": "Applied to ...", "external_url": "..."}`.
- Duplicate application for the same offer returns `{"applied": false, "message": "Already applied to this offer", "external_url": "..."}`.
- Unknown `offer_id` returns `404 Not Found`.

---

## 3. User, Profile, Settings, Feedback & CVs (`/user`)

| Method | Path | Query / Body | Response / Status |
|---|---|---|---|
| `GET` | `/user/profile` | — | `UserProfileDto` (`name`, `phone`, `email`) |
| `PUT` | `/user/profile` | `UpdateProfileRequestDto` | `UserProfileDto` |
| `GET` | `/user/settings` | — | `UserSettingsDto` |
| `PUT` | `/user/settings` | `UpdateUserSettingsRequestDto` (partial updates supported) | `UserSettingsDto` |
| `POST` | `/user/feedback` | `FeedbackRequestDto` (`category`, `message`, `contact_email`) | `FeedbackResponseDto` (`400` if empty or `> 2000` chars) |
| `GET` | `/user/applications` | — | `ApplicationsResponseDto` |
| `GET` | `/user/cvs` | — | `CvsResponseDto` |
| `POST` | `/user/cvs` | `CreateCvRequestDto` (`{"file_name": "new-cv.pdf"}`) | `CvDto` (blank structured sections, only user contact header) |
| `POST` | `/user/cvs/upload` | Multipart form `file` (`.pdf`) | `CvDto` (`400` invalid/encrypted/`>10` pages/`>10` CVs, `413` if `> 5 MB`) |
| `PUT` | `/user/cvs/{cv_id}` | `UpdateCvContentRequestDto` | `CvDto` (`404` if missing) |
| `DELETE` | `/user/cvs/{cv_id}` | — | `DeleteCvResponseDto` (`404` if missing) |
| `GET` | `/user/cvs/{cv_id}/download` | — | Raw `application/pdf` stream with `Cache-Control: no-store` (`404` if missing) |

### Upload & Localization Rules

- **Limits:** `5 MB` (`MAX_CV_FILE_SIZE_BYTES = 5242880`), `10` pages (`MAX_CV_PAGES = 10`), `10` CVs per user (`MAX_CVS_PER_USER = 10`), non-encrypted `.pdf` required.
- **Automatic Location Sync:** On `POST /user/cvs/upload` and `PUT /user/cvs/{cv_id}`, `sync_user_location_from_cv_text` inspects CV text for recognized cities; if found, updates `UserModel.latitude`/`longitude` and `UserSettingsModel.city`. If no city is present, user location is left unchanged.

---

## 4. AI Assistant (`/assistant`)

| Method | Path | Body | Response Model |
|---|---|---|---|
| `GET` | `/assistant/health` | — | `AssistantHealthDto` (`reachable`, `llm_base_url`, `chat_model`, `embedding_model`, `error`) |
| `POST` | `/assistant/chat` | `AssistantChatRequestDto` (`message`, `cv_id`, `history`) | `AssistantChatResponseDto` (`reply`, `model`, `updated_cv`, `suggested_offers`) |

- If the LLM invokes `update_cv`, the backend edits the selected CV, regenerates its PDF on disk, runs automatic location extraction if a city was added/updated, and returns `updated_cv` so the Android app immediately refreshes PDF thumbnails, Profile city, Home carousels, and Search results.
- Returns `503 Service Unavailable` if the configured `LLM_BASE_URL` server is unreachable.

---

## 5. Notifications & Daily Digest (`/notifications`)

| Method | Path | Query / Path Params | Response Model |
|---|---|---|---|
| `GET` | `/notifications` | — | `NotificationsResponseDto` (`notifications: list[NotificationDto]`) |
| `PATCH` | `/notifications/{notification_id}/read` | Path: `notification_id` | `NotificationReadResponseDto` (`404` if not found) |
| `POST` | `/notifications/daily-digest` | Query: `force` (`false`) | `DailyDigestResultDto` (`pushed`, `message`, `notification`) |
| `GET` | `/notifications/digest/status` | — | `DailyDigestStatusDto` |

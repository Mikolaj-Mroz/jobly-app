# Jobly Testing Guide

Both **Jobly** (Android) and **JoblyBackend** (FastAPI) include specification-driven (black-box) unit tests and full end-to-end functional tests that verify public contracts, boundary limits, adversarial inputs, and live user workflows rather than internal implementation details.

---

## Test Suite Summary

| Suite | Runner | Test Files | Total Tests |
|---|---|---|---|
| Backend (`JoblyBackend`) | `pytest` + `httpx.ASGITransport` | 7 files (`tests/test_*.py`) | **64** |
| Android JVM Unit (`Jobly`) | Gradle (`testDebugUnitTest`) | 6 classes | **23** |
| Android Instrumented UI & E2E (`Jobly`) | AndroidJUnit4 (`connectedDebugAndroidTest`) | 2 classes | **6** |

---

## 1. Backend Tests (`JoblyBackend`)

Run all backend tests (uses isolated `tmp_path` SQLite DB and storage directories via `tests/conftest.py`):

```powershell
cd JoblyBackend
.\.venv\Scripts\pytest.exe -v
```

| Test File | Tests | Coverage |
|---|---|---|
| `tests/test_spec_unit.py` | 31 | Black-box specification unit tests: Polish diacritic/locative city extraction & skip-on-missing, Unicode PDF round-trip, boundary rejections (`""`, non-PDF, corrupt, `>5 MB`, `>10 pages`, exact `10 pages` pass), filename traversal & truncation hardening, recommendation score `[0..100]` bounds under `None`/extreme inputs, monotonicity, aliasing, determinism, and scraper resilience against malformed JSON. |
| `tests/test_search.py` | 12 | Search engine unit + HTTP E2E tests: field relevance weighting, multi-word `AND` semantics, Polish diacritics & `Warsaw`/`Warszawa` aliasing, tech aliases (`k8s`, `ts`), fuzzy typo tolerance (`pyhton`, `kubernets`), facet filtering (`level`, `contract_type`, `work_mode`, `city`, `min_salary`), all 5 sort modes, pagination `total` preservation, and adversarial query handling on `GET /offers/search`. |
| `tests/test_functional_e2e.py` | 5 | End-to-end HTTP journeys against FastAPI + SQLite: onboarding -> PDF upload in Gdansk -> `/offers/around-you` geo re-ranking -> no-location PDF upload skips update -> CV edit to Wroclaw -> download & delete `404`; upload limit/security matrix; application idempotency & daily digest unread counters; SQLi/XSS-safe feedback validation; and empty CV creation verifying zero mock template leakage. |
| `tests/test_user_and_pdf.py` | 5 | Profile CRUD, CV create/edit/download/upload lifecycle, validation limits, settings & feedback endpoints, and CV location extraction/skip flows. |
| `tests/test_assistant_and_scraper.py` | 5 | AI Assistant tool calling (`update_cv` editing PDF on disk cleanly), scraper deduplication, JustJoin.it pagination/salary/skills/coords parsing, and `/offers/scrape/status`. |
| `tests/test_notifications.py` | 3 | Notification listing, idempotent mark-read, daily digest deduplication & settings respect, and scheduler status. |
| `tests/test_offers_and_recommendations.py` | 3 | Around-you / best-for-you endpoints, apply deduplication, and multi-signal ranking (aliases, seniority, geo, applied novelty). |

---

## 2. Android JVM Unit Tests (`Jobly`)

Run fast local JVM tests without an emulator:

```powershell
cd Jobly
.\gradlew.bat testDebugUnitTest
```

Per-file XML results are written to `app/build/test-results/testDebugUnitTest/`.

| Test Class | Tests | Coverage |
|---|---|---|
| `BlackBoxSpecUnitTest` | 6 | Adversarial/malformed Markdown inputs, offer description formatting edge cases & `Offer URL` hiding, `HomeViewModel` 5-card carousel cap across corpus sizes (`0..50`) and failure recovery, `AssistantViewModel` blank-prompt filtering & sticky active CV selection, and `NotificationsViewModel` unread-count invariants. |
| `SearchViewModelTest` | 4 | Initial load, `250 ms` search debounce cancellation & recent-query deduplication/6-item cap, facet toggles / `clearFilters` / `clearAll` / sort parameter propagation, and network error + `retry()` recovery. |
| `HomeViewModelTest` | 4 | Initial seed loading, search query filtering/clear, CV add + apply duplicate handling, and 5-offer Home carousel cap. |
| `AssistantViewModelTest` | 3 | Health model detection, non-duplicated chat history + `update_cv` sync back to `UserRepository`, network failure handling, and active CV selection forwarding `cvId`. |
| `MarkdownParserTest` | 3 | Block parsing (headings, nested lists, fenced code, quotes, dividers), inline styling (`bold`, `italic`, `code`, `strikethrough`, `snake_case`, links), and `formatOfferDescriptionMarkdown`. |
| `NotificationsAndProfileViewModelTest` | 3 | Notification read/daily-digest flows, profile/onboarding flows, and `RemoteOffersRepository` / `RemoteUserRepository` / `RemoteNotificationsRepository` Retrofit mapping (including `searchOffers` & client-side 5 MB / `%PDF-` pre-checks). |

---

## 3. Android Instrumented UI & Live E2E Tests (`Jobly`)

### Prerequisites
1. A booted Android emulator or connected device (`adb devices` shows `device`), e.g. `Pixel_3a_API_35`.
2. **JoblyBackend** running on port `8000` (`http://10.0.2.2:8000` reachable from emulator).

### Command

```powershell
cd Jobly
.\gradlew.bat connectedDebugAndroidTest
```

HTML report: `app/build/reports/androidTests/connected/debug/index.html`.

| Test Class | Tests | Coverage |
|---|---|---|
| `JoblyFunctionalUiTest` | 5 | Black-box Compose UI interaction tests driven purely by visible text, content descriptions, scrolling, and clicks: (1) `OfferDetailScreen` formatted **About this role** + Apply action, (2) `AssistantScreen` `+` CV menu selection + **+ New empty CV** + **Upload PDF CV...**, (3) `ProfileScreen` inline expand/collapse of **My applications** and **My CVs**, (4) `HelpFeedbackScreen` form input & confirmation banner, (5) `SearchScreen` query entry, `Remote` filter chip, sort menu change, and offer card tap. |
| `JoblyLiveE2eUiTest` | 1 | Launches the real `MainActivity` against the live backend: skips/handles onboarding -> verifies Home -> opens Search via `"Search your new job"` -> types `"Python"` -> opens a live offer into `OfferDetailScreen` -> presses Back to Search -> presses Back to Home -> visits Assistant (`+` CV menu), Notifications, Profile, and Sidebar Drawer -> Settings -> Back. |

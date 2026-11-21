import asyncio
import hashlib
import json
import logging
import math
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Any
import httpx
from bs4 import BeautifulSoup
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
from app.models import JobOfferModel, NotificationModel

logger = logging.getLogger(__name__)

_ACCENTS = ["YELLOW", "BLUE", "PINK", "GREEN", "YELLOW_ALT"]
_DEFAULT_LAT = 50.0647
_DEFAULT_LON = 19.9450

_CONTRACT_TYPE_MAP = {
    "b2b": "B2B",
    "permanent": "UoP",
    "mandate_contract": "UZ",
    "internship": "Internship",
    "specific_task_contract": "UoD",
}
_EXPERIENCE_LEVEL_MAP = {
    "junior": "JUNIOR",
    "mid": "MID",
    "senior": "SENIOR",
    "manager": "MANAGER",
    "c_level": "C-LEVEL",
}
_WORKING_TIME_MAP = {
    "full_time": "Full-Time",
    "part_time": "Part-Time",
    "practice_internship": "Internship",
    "freelance": "Freelance",
}
_SALARY_UNIT_MAP = {
    "month": "month",
    "hour": "hour",
    "day": "day",
    "year": "year",
}


@dataclass
class ScrapedOffer:
    external_url: str
    title: str
    company_name: str
    salary_range: str
    salary_unit: str
    skills: list[str]
    level: str
    contract_type: str
    work_schedule: str
    description: str
    source: str
    latitude: float = 50.0647
    longitude: float = 19.9450


class BaseJobScraper(ABC):
    source_name: str

    @abstractmethod
    async def fetch(self, client: httpx.AsyncClient) -> list[ScrapedOffer]:
        raise NotImplementedError


class ArbeitnowScraper(BaseJobScraper):
    source_name = "arbeitnow"
    endpoint = "https://www.arbeitnow.com/api/job-board-api"

    async def fetch(self, client: httpx.AsyncClient) -> list[ScrapedOffer]:
        resp = await client.get(self.endpoint, timeout=20.0)
        resp.raise_for_status()
        payload = resp.json()
        items = payload.get("data", [])[:15]
        results: list[ScrapedOffer] = []
        for item in items:
            url = item.get("url") or item.get("slug") or ""
            title = (item.get("title") or "").strip()
            company = (item.get("company_name") or "Unknown Company").strip()
            if not url or not title:
                continue
            tags = [str(t) for t in (item.get("tags") or [])[:4]]
            raw_desc = BeautifulSoup(item.get("description") or "", "html.parser").get_text(" ", strip=True)
            results.append(
                ScrapedOffer(
                    external_url=url,
                    title=title,
                    company_name=company,
                    salary_range="14 000 - 22 000",
                    salary_unit="PLN/month",
                    skills=tags or ["Backend", "Cloud"],
                    level="MID",
                    contract_type="Remote" if item.get("remote") else "UoP / B2B",
                    work_schedule="Full-Time",
                    description=raw_desc[:600],
                    source=self.source_name,
                )
            )
        return results


class RemoteOkScraper(BaseJobScraper):
    source_name = "remoteok"
    endpoint = "https://remoteok.com/api"

    async def fetch(self, client: httpx.AsyncClient) -> list[ScrapedOffer]:
        resp = await client.get(
            self.endpoint,
            headers={"User-Agent": "JoblyBackend/1.0"},
            timeout=20.0,
        )
        resp.raise_for_status()
        payload = resp.json()
        items = [x for x in payload if isinstance(x, dict) and x.get("position")][:15]
        results: list[ScrapedOffer] = []
        for item in items:
            url = item.get("url") or item.get("apply_url") or ""
            title = (item.get("position") or "").strip()
            company = (item.get("company") or "Remote Company").strip()
            if not url or not title:
                continue
            tags = [str(t) for t in (item.get("tags") or [])[:4]]
            sal_min = item.get("salary_min")
            sal_max = item.get("salary_max")
            if sal_min and sal_max:
                sal_range = f"{int(sal_min):,} - {int(sal_max):,}".replace(",", " ")
                sal_unit = "USD/year"
            else:
                sal_range = "16 000 - 24 000"
                sal_unit = "PLN/month"
            raw_desc = BeautifulSoup(item.get("description") or "", "html.parser").get_text(" ", strip=True)
            results.append(
                ScrapedOffer(
                    external_url=url,
                    title=title,
                    company_name=company,
                    salary_range=sal_range,
                    salary_unit=sal_unit,
                    skills=tags or ["Python", "API"],
                    level="MID",
                    contract_type="B2B",
                    work_schedule="Full-Time",
                    description=raw_desc[:600],
                    source=self.source_name,
                )
            )
        return results


class HtmlJobBoardScraper(BaseJobScraper):
    """Template HTML scraper using BeautifulSoup4 for external job boards without JSON APIs."""

    def __init__(self, source_name: str, url: str) -> None:
        self.source_name = source_name
        self.url = url

    async def fetch(self, client: httpx.AsyncClient) -> list[ScrapedOffer]:
        resp = await client.get(self.url, timeout=20.0)
        resp.raise_for_status()
        return self.parse_html(resp.text)

    def parse_html(self, html: str) -> list[ScrapedOffer]:
        soup = BeautifulSoup(html, "html.parser")
        results: list[ScrapedOffer] = []
        for card in soup.select("[data-job-card]"):
            title_el = card.select_one("[data-job-title]")
            company_el = card.select_one("[data-job-company]")
            link_el = card.select_one("a[href]")
            if not title_el or not link_el:
                continue
            skills = [el.get_text(strip=True) for el in card.select("[data-job-skill]")][:4]
            results.append(
                ScrapedOffer(
                    external_url=str(link_el["href"]),
                    title=title_el.get_text(strip=True),
                    company_name=company_el.get_text(strip=True) if company_el else "Company",
                    salary_range="15 000 - 21 000",
                    salary_unit="PLN/month",
                    skills=skills or ["Tech"],
                    level="MID",
                    contract_type="B2B",
                    work_schedule="Full-Time",
                    description=card.get_text(" ", strip=True)[:600],
                    source=self.source_name,
                )
            )
        return results


class JustJoinItScraper(BaseJobScraper):
    """Scraper for JustJoin.it's public candidate-api paginated JSON endpoint."""

    source_name = "justjoin.it"
    endpoint = "https://justjoin.it/api/candidate-api/offers"
    offer_url_prefix = "https://justjoin.it/job-offer/"

    def __init__(
        self,
        items_per_page: int = 50,
        max_pages: int = 2,
        page_delay_seconds: float = 0.5,
    ) -> None:
        self.items_per_page = max(1, items_per_page)
        self.max_pages = max(1, max_pages)
        self.page_delay_seconds = max(0.0, page_delay_seconds)

    async def fetch(self, client: httpx.AsyncClient) -> list[ScrapedOffer]:
        """Fetch paginated offers from JustJoin.it up to `max_pages` pages."""
        results: list[ScrapedOffer] = []
        cursor: Any = 0
        headers = {
            "User-Agent": "JoblyBackend/1.0",
            "Accept": "application/json",
        }

        for page_index in range(self.max_pages):
            if page_index > 0 and self.page_delay_seconds > 0:
                await asyncio.sleep(self.page_delay_seconds)

            resp = await client.get(
                self.endpoint,
                params={"from": cursor, "itemsCount": self.items_per_page},
                headers=headers,
                timeout=20.0,
            )
            resp.raise_for_status()
            payload = resp.json()
            items = payload.get("data") or []
            if not isinstance(items, list) or not items:
                break

            for item in items:
                if not isinstance(item, dict):
                    continue
                parsed = self._parse_item(item)
                if parsed is not None:
                    results.append(parsed)

            meta = payload.get("meta") if isinstance(payload, dict) else None
            next_block = meta.get("next") if isinstance(meta, dict) else None
            next_cursor = next_block.get("cursor") if isinstance(next_block, dict) else None
            if next_cursor is None or next_cursor == cursor:
                break
            cursor = next_cursor

        return results

    def _parse_item(self, item: dict[str, Any]) -> ScrapedOffer | None:
        title = str(item.get("title") or "").strip()
        slug = str(item.get("slug") or "").strip()
        guid = str(item.get("guid") or "").strip()
        identifier = slug or guid
        if not title or not identifier:
            return None

        external_url = f"{self.offer_url_prefix}{identifier}"
        company_name = str(item.get("companyName") or "").strip() or "Unknown Company"
        salary_range, salary_unit, contract_type = self._extract_compensation(
            item.get("employmentTypes")
        )
        skills = self._extract_skills(item)
        raw_level = str(item.get("experienceLevel") or "").strip().lower()
        level = _EXPERIENCE_LEVEL_MAP.get(raw_level) or (
            raw_level.upper() if raw_level else "MID"
        )
        raw_schedule = str(item.get("workingTime") or "").strip().lower()
        work_schedule = _WORKING_TIME_MAP.get(raw_schedule, "Full-Time")
        latitude, longitude = self._extract_coordinates(item)
        description = self._build_description(item, external_url)

        return ScrapedOffer(
            external_url=external_url,
            title=title,
            company_name=company_name,
            salary_range=salary_range,
            salary_unit=salary_unit,
            skills=skills,
            level=level,
            contract_type=contract_type,
            work_schedule=work_schedule,
            description=description,
            source=self.source_name,
            latitude=latitude,
            longitude=longitude,
        )

    @staticmethod
    def _format_amount(value: Any) -> str | None:
        if value is None:
            return None
        try:
            num = float(value)
        except (TypeError, ValueError):
            return None
        if not math.isfinite(num):
            return None
        return f"{int(round(num)):,}".replace(",", " ")

    @classmethod
    def _extract_compensation(
        cls, employment_types: Any
    ) -> tuple[str, str, str]:
        """Select the highest-priority employment entry and format salary & contract type."""
        entries = [e for e in (employment_types or []) if isinstance(e, dict)]
        if not entries:
            return "Undisclosed", "", "B2B"

        def _has_salary(entry: dict[str, Any]) -> bool:
            return (
                cls._format_amount(entry.get("from")) is not None
                or cls._format_amount(entry.get("to")) is not None
            )

        def _priority(entry: dict[str, Any]) -> tuple[int, int, int]:
            has_sal = _has_salary(entry)
            is_original = str(entry.get("currencySource") or "").strip().lower() == "original"
            is_pln = str(entry.get("currency") or "").strip().upper() == "PLN"
            return (
                0 if has_sal else 1,
                0 if is_original else 1,
                (0 if is_pln else 1) if has_sal else 0,
            )

        chosen = min(entries, key=_priority)
        raw_contract = str(chosen.get("type") or "").strip().lower()
        contract_type = _CONTRACT_TYPE_MAP.get(raw_contract) or (
            raw_contract.replace("_", " ").upper() if raw_contract else "B2B"
        )

        low = cls._format_amount(chosen.get("from"))
        high = cls._format_amount(chosen.get("to"))
        if low and high:
            salary_range = f"{low} - {high}"
        elif low:
            salary_range = f"From {low}"
        elif high:
            salary_range = f"Up to {high}"
        else:
            return "Undisclosed", "", contract_type

        raw_unit = str(chosen.get("unit") or "month").strip().lower()
        unit_norm = _SALARY_UNIT_MAP.get(raw_unit, raw_unit or "month")
        currency = str(chosen.get("currency") or "PLN").strip().upper() or "PLN"
        return salary_range, f"{currency}/{unit_norm}", contract_type

    @staticmethod
    def _extract_skills(item: dict[str, Any]) -> list[str]:
        required = item.get("requiredSkills")
        scored: list[tuple[int, int, str]] = []
        if isinstance(required, list):
            for idx, entry in enumerate(required):
                if not isinstance(entry, dict):
                    continue
                name = str(entry.get("name") or "").strip()
                if not name:
                    continue
                try:
                    level = int(entry.get("level") or 0)
                except (TypeError, ValueError):
                    level = 0
                scored.append((-level, idx, name))

        if scored:
            scored.sort()
            return [name for _, _, name in scored[:4]]

        nice_to_have = item.get("niceToHaveSkills")
        if isinstance(nice_to_have, list):
            fallback_skills = [
                str(entry.get("name") or "").strip()
                for entry in nice_to_have
                if isinstance(entry, dict) and str(entry.get("name") or "").strip()
            ][:4]
            if fallback_skills:
                return fallback_skills

        category = item.get("category")
        if isinstance(category, dict) and str(category.get("key") or "").strip():
            return [str(category["key"]).strip()]

        return ["IT"]

    @staticmethod
    def _parse_coord_pair(container: Any) -> tuple[float, float] | None:
        if not isinstance(container, dict):
            return None
        raw_lat = container.get("latitude")
        raw_lon = container.get("longitude")
        if raw_lat is None or raw_lon is None:
            return None
        try:
            lat = float(raw_lat)
            lon = float(raw_lon)
        except (TypeError, ValueError):
            return None
        if not (math.isfinite(lat) and math.isfinite(lon)):
            return None
        return lat, lon

    @classmethod
    def _extract_coordinates(cls, item: dict[str, Any]) -> tuple[float, float]:
        top_coords = cls._parse_coord_pair(item)
        if top_coords is not None:
            return top_coords

        locations = item.get("locations")
        if isinstance(locations, list):
            for loc in locations:
                coords = cls._parse_coord_pair(loc)
                if coords is not None:
                    return coords

        return _DEFAULT_LAT, _DEFAULT_LON

    @staticmethod
    def _build_description(item: dict[str, Any], external_url: str) -> str:
        parts: list[str] = []

        workplace = str(item.get("workplaceType") or "").strip().replace("_", " ").title()
        city = str(item.get("city") or "").strip()
        street = str(item.get("street") or "").strip()
        location_bits = ", ".join(b for b in [city, street] if b)
        if workplace and location_bits:
            parts.append(f"Workplace: {workplace} ({location_bits})")
        elif workplace:
            parts.append(f"Workplace: {workplace}")
        elif location_bits:
            parts.append(f"Location: {location_bits}")

        category = item.get("category")
        if isinstance(category, dict) and str(category.get("key") or "").strip():
            parts.append(f"Category: {str(category['key']).strip()}")

        required = item.get("requiredSkills")
        if isinstance(required, list):
            req_labels = []
            for entry in required:
                if not isinstance(entry, dict):
                    continue
                name = str(entry.get("name") or "").strip()
                if not name:
                    continue
                lvl = entry.get("level")
                req_labels.append(f"{name} ({lvl}/5)" if lvl is not None else name)
            if req_labels:
                parts.append("Required skills: " + ", ".join(req_labels[:8]))

        nice = item.get("niceToHaveSkills")
        if isinstance(nice, list):
            nice_labels = [
                str(entry.get("name") or "").strip()
                for entry in nice
                if isinstance(entry, dict) and str(entry.get("name") or "").strip()
            ]
            if nice_labels:
                parts.append("Nice to have: " + ", ".join(nice_labels[:6]))

        languages = item.get("languages")
        if isinstance(languages, list):
            lang_labels = []
            for lang in languages:
                if not isinstance(lang, dict):
                    continue
                code = str(lang.get("code") or "").strip()
                lvl = str(lang.get("level") or "").strip()
                if code:
                    lang_labels.append(f"{code} {lvl}".strip())
            if lang_labels:
                parts.append("Languages: " + ", ".join(lang_labels))

        parts.append(f"Offer URL: {external_url}")
        return " | ".join(parts)[:1000]


class JobIngestionService:
    def __init__(self, scrapers: list[BaseJobScraper] | None = None) -> None:
        self.scrapers: list[BaseJobScraper] = (
            list(scrapers)
            if scrapers is not None
            else [
                JustJoinItScraper(),
                ArbeitnowScraper(),
                RemoteOkScraper(),
            ]
        )

    @property
    def source_names(self) -> list[str]:
        return [scraper.source_name for scraper in self.scrapers]

    async def sync_all(
        self,
        session: AsyncSession,
        user_id: str = "default_user",
        client: httpx.AsyncClient | None = None,
        source_filter: str | None = None,
    ) -> tuple[int, int, int, int]:
        """Returns (sources_checked, fetched_count, inserted_count, notifications_created)."""
        if source_filter:
            normalized = source_filter.strip().lower()
            target_scrapers = [
                s for s in self.scrapers if s.source_name.lower() == normalized
            ]
        else:
            target_scrapers = list(self.scrapers)

        owns_client = client is None
        http_client = client or httpx.AsyncClient(follow_redirects=True)
        fetched: list[ScrapedOffer] = []
        try:
            for scraper in target_scrapers:
                try:
                    fetched.extend(await scraper.fetch(http_client))
                except Exception as exc:
                    logger.warning("Scraper '%s' failed: %s", scraper.source_name, exc)
                    continue
        finally:
            if owns_client:
                await http_client.aclose()

        existing_urls = set(
            (await session.execute(select(JobOfferModel.external_url))).scalars().all()
        )
        inserted = 0
        notifications_created = 0

        for item in fetched:
            if item.external_url in existing_urls:
                continue
            existing_urls.add(item.external_url)
            offer_id = "ext_" + hashlib.sha1(item.external_url.encode("utf-8")).hexdigest()[:12]
            accent = _ACCENTS[inserted % len(_ACCENTS)]
            initial = (item.company_name[:1] or "A").upper()
            offer = JobOfferModel(
                id=offer_id,
                initial=initial,
                title=item.title,
                salary_range=item.salary_range,
                salary_unit=item.salary_unit,
                skills_json=json.dumps(item.skills),
                company_name=item.company_name,
                level=item.level,
                contract_type=item.contract_type,
                work_schedule=item.work_schedule,
                accent=accent,
                description=item.description,
                source=item.source,
                external_url=item.external_url,
                latitude=item.latitude,
                longitude=item.longitude,
            )
            session.add(offer)
            inserted += 1

            if notifications_created < 2:
                notif = NotificationModel(
                    id=f"notif_{offer_id}",
                    user_id=user_id,
                    heading="New job offer for you",
                    offer_id=offer_id,
                    is_read=False,
                )
                session.add(notif)
                notifications_created += 1

        if inserted > 0:
            await session.commit()

        return len(target_scrapers), len(fetched), inserted, notifications_created

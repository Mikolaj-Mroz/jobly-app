"""Full-text + faceted job search engine with typo tolerance, diacritic folding, and personalized scoring."""

from __future__ import annotations

from dataclasses import replace
from difflib import SequenceMatcher
import math
import re
import unicodedata

from app.models import CvModel, JobOfferModel
from app.services.recommendation_service import (
    ScoredOffer,
    _PHRASE_ALIASES,
    _STOPWORDS,
    _TOKEN_ALIASES,
    _is_remote_offer,
    _offer_timestamp,
    haversine_km,
    parse_json_list,
    score_best_for_you,
)

_SALARY_NUM_RE = re.compile(r"\d+(?:[ \u00a0,]\d{3})*|\d+")
_SEARCH_TOKEN_RE = re.compile(r"[a-z0-9+#.]+")

_VALID_SORTS = {"relevance", "best_match", "salary_desc", "nearest", "newest"}


def _fold_diacritics(text: str) -> str:
    """Lowercase text and strip accents/diacritics (including Polish ł -> l)."""
    if not text:
        return ""
    pre = text.replace("ł", "l").replace("Ł", "L")
    decomposed = unicodedata.normalize("NFKD", pre)
    stripped = "".join(ch for ch in decomposed if not unicodedata.combining(ch))
    return stripped.lower()


def _normalize_city_aliases(folded_text: str) -> str:
    return re.sub(r"\bwarsaw\b", "warszawa", folded_text)


def _canonicalize_search_text(text: str) -> str:
    lowered = _normalize_city_aliases(_fold_diacritics(text))
    for src, dst in _PHRASE_ALIASES:
        lowered = lowered.replace(src, f" {dst} ")
    return lowered


def _extract_field_tokens(text: str) -> tuple[set[str], set[str]]:
    """Return `(all_folded_tokens, canonical_tokens)` for a text field."""
    canonical = _canonicalize_search_text(text)
    raw_tokens: set[str] = set()
    canon_tokens: set[str] = set()
    for raw_tok in _SEARCH_TOKEN_RE.findall(canonical):
        tok = raw_tok.strip(".")
        if not tok:
            continue
        raw_tokens.add(tok)
        aliased = _TOKEN_ALIASES.get(tok, tok)
        canon_tokens.add(tok)
        canon_tokens.add(aliased)
    return raw_tokens, canon_tokens


def _extract_query_terms(raw_query: str) -> list[tuple[str, str]]:
    """Extract query terms as `(raw_term, canonical_term)` pairs, falling back to non-stopword-filtered tokens."""
    canonical = _canonicalize_search_text(raw_query)
    extracted: list[tuple[str, str]] = []
    fallback: list[tuple[str, str]] = []
    for raw_tok in _SEARCH_TOKEN_RE.findall(canonical):
        tok = raw_tok.strip(".")
        if not tok:
            continue
        aliased = _TOKEN_ALIASES.get(tok, tok)
        fallback.append((tok, aliased))
        if tok not in _STOPWORDS:
            extracted.append((tok, aliased))

    if extracted:
        return extracted
    if fallback:
        return fallback

    parts = [p for p in _fold_diacritics(raw_query).split() if p]
    return [(p, p) for p in parts]


def parse_salary_bounds(salary_range: str, salary_unit: str = "") -> tuple[int | None, int | None]:
    """Parse `(min_salary, max_salary)` from offer salary strings; returns `(None, None)` if undisclosed."""
    if not salary_range or "undisclosed" in salary_range.lower():
        return (None, None)

    nums: list[int] = []
    for match in _SALARY_NUM_RE.findall(salary_range):
        digits = re.sub(r"[ \u00a0,]", "", match)
        if digits.isdigit():
            nums.append(int(digits))

    if not nums:
        return (None, None)

    min_sal = min(nums)
    max_sal = max(nums)

    # Normalize genuine hourly rates (e.g. "120 - 160 PLN/hour") to monthly (~160h) for uniform comparison
    if "hour" in (salary_unit or "").lower() and max_sal < 1000:
        min_sal *= 160
        max_sal *= 160

    return (min_sal, max_sal)


def detect_work_mode(offer: JobOfferModel) -> str:
    """Infer normalized work mode (`remote`, `hybrid`, or `office`) for an offer."""
    combined = _fold_diacritics(
        " ".join([offer.description or "", offer.work_schedule or "", offer.title or ""])
    )
    if (
        "workplace: remote" in combined
        or "remote" in combined
        or "zdalna" in combined
        or "zdalnie" in combined
    ):
        return "remote"
    if "workplace: hybrid" in combined or "hybrid" in combined or "hybryd" in combined:
        return "hybrid"
    return "office"


def _matches_facets(
    offer: JobOfferModel,
    level: str | None,
    contract_type: str | None,
    work_mode: str | None,
    city: str | None,
    min_salary: int | None,
) -> bool:
    if level and level.strip():
        if offer.level.strip().upper() != level.strip().upper():
            return False

    if contract_type and contract_type.strip():
        if _fold_diacritics(offer.contract_type).strip() != _fold_diacritics(contract_type).strip():
            return False

    if work_mode and work_mode.strip():
        target_mode = _fold_diacritics(work_mode).strip()
        detected = detect_work_mode(offer)
        if target_mode == "remote":
            if detected != "remote" and not _is_remote_offer(offer):
                return False
        elif detected != target_mode:
            return False

    if city and city.strip():
        folded_city = _normalize_city_aliases(_fold_diacritics(city).strip())
        haystack = _normalize_city_aliases(
            _fold_diacritics(f"{offer.description or ''} {offer.title or ''}")
        )
        if folded_city not in haystack:
            return False

    if min_salary is not None and min_salary > 0:
        _, max_sal = parse_salary_bounds(offer.salary_range, offer.salary_unit)
        if max_sal is None or max_sal < min_salary:
            return False

    return True


def _is_fuzzy_match(term: str, candidates: set[str]) -> bool:
    if len(term) < 4:
        return False
    for cand in candidates:
        if abs(len(cand) - len(term)) > 3:
            continue
        if SequenceMatcher(None, term, cand).ratio() >= 0.80:
            return True
    return False


def _score_query_match(offer: JobOfferModel, raw_query: str) -> tuple[bool, float]:
    """Check full-text query match (strict AND across terms) and return `(matched, relevance_score)`."""
    trimmed = (raw_query or "").strip()
    if not trimmed:
        return (True, 0.0)

    terms = _extract_query_terms(trimmed)
    if not terms:
        return (True, 0.0)

    skills = [str(s) for s in parse_json_list(offer.skills_json)]
    title_folded = _normalize_city_aliases(_fold_diacritics(offer.title or ""))
    skills_folded = _normalize_city_aliases(_fold_diacritics(" ".join(skills)))
    company_folded = _normalize_city_aliases(_fold_diacritics(offer.company_name or ""))
    other_folded = _normalize_city_aliases(
        _fold_diacritics(
            " ".join(
                [
                    offer.description or "",
                    offer.level or "",
                    offer.contract_type or "",
                    offer.work_schedule or "",
                ]
            )
        )
    )

    _, title_tokens = _extract_field_tokens(offer.title or "")
    _, skills_tokens = _extract_field_tokens(" ".join(skills))
    _, company_tokens = _extract_field_tokens(offer.company_name or "")
    _, other_tokens = _extract_field_tokens(
        f"{offer.description or ''} {offer.level or ''} {offer.contract_type or ''} {offer.work_schedule or ''}"
    )

    fuzzy_pool = title_tokens | skills_tokens | company_tokens

    total_score = 0.0
    for raw_term, canon_term in terms:
        variants = {raw_term, canon_term}
        term_score = 0.0

        # 1. Exact token or alias match
        if variants & title_tokens:
            term_score += 20.0
        if variants & skills_tokens:
            term_score += 18.0
        if variants & company_tokens:
            term_score += 14.0
        if variants & other_tokens:
            term_score += 6.0

        # 2. Prefix / substring match (for terms length >= 2) if exact didn't hit that field
        if len(raw_term) >= 2:
            if not (variants & title_tokens) and any(v in title_folded for v in variants):
                term_score += 12.0
            if not (variants & skills_tokens) and any(v in skills_folded for v in variants):
                term_score += 10.0
            if not (variants & company_tokens) and any(v in company_folded for v in variants):
                term_score += 8.0
            if not (variants & other_tokens) and any(v in other_folded for v in variants):
                term_score += 4.0

        # 3. Typo tolerance via fuzzy matching on title / skills / company tokens
        if term_score == 0.0 and (
            _is_fuzzy_match(raw_term, fuzzy_pool) or _is_fuzzy_match(canon_term, fuzzy_pool)
        ):
            term_score += 9.0

        if term_score == 0.0:
            return (False, 0.0)

        total_score += term_score

    # Exact full-phrase bonuses
    folded_phrase = _normalize_city_aliases(_fold_diacritics(trimmed))
    if folded_phrase:
        if folded_phrase in title_folded:
            total_score += 40.0
        if folded_phrase in skills_folded:
            total_score += 25.0
        if folded_phrase in company_folded:
            total_score += 20.0
        if folded_phrase in other_folded:
            total_score += 10.0

    return (True, total_score)


def search_offers(
    offers: list[JobOfferModel],
    cv: CvModel | None,
    query: str = "",
    level: str | None = None,
    contract_type: str | None = None,
    work_mode: str | None = None,
    city: str | None = None,
    min_salary: int | None = None,
    sort: str = "relevance",
    user_lat: float | None = None,
    user_lon: float | None = None,
    applied_offers: list[JobOfferModel] | None = None,
    limit: int = 50,
    offset: int = 0,
) -> tuple[int, list[ScoredOffer]]:
    """Filter, rank, sort, and paginate job offers for the search screen."""
    normalized_sort = sort.strip().lower() if sort else "relevance"
    if normalized_sort not in _VALID_SORTS:
        normalized_sort = "relevance"

    personalized = score_best_for_you(
        offers=offers,
        cv=cv,
        query="",
        user_lat=user_lat,
        user_lon=user_lon,
        applied_offers=applied_offers,
    )
    scored_by_id = {entry.offer.id: entry for entry in personalized}

    matched_entries: list[tuple[ScoredOffer, float, int | None, int | None, float]] = []
    for offer in offers:
        if not _matches_facets(offer, level, contract_type, work_mode, city, min_salary):
            continue
        is_hit, text_score = _score_query_match(offer, query)
        if not is_hit:
            continue

        scored_offer = scored_by_id.get(offer.id) or ScoredOffer(
            offer=offer, match_score=50, match_reasons=[]
        )
        min_sal, max_sal = parse_salary_bounds(offer.salary_range, offer.salary_unit)

        if (
            user_lat is not None
            and user_lon is not None
            and offer.latitude is not None
            and offer.longitude is not None
        ):
            dist_km = haversine_km(user_lat, user_lon, offer.latitude, offer.longitude)
        else:
            dist_km = math.inf

        matched_entries.append((scored_offer, text_score, max_sal, min_sal, dist_km))

    if normalized_sort == "best_match":
        matched_entries.sort(
            key=lambda item: (
                -(item[0].match_score or 0),
                -item[1],
                -_offer_timestamp(item[0].offer.created_at),
            )
        )
    elif normalized_sort == "salary_desc":
        matched_entries.sort(
            key=lambda item: (
                -(item[2] if item[2] is not None else -1),
                -(item[3] if item[3] is not None else -1),
                -item[1],
            )
        )
    elif normalized_sort == "nearest":
        matched_entries.sort(key=lambda item: (item[4], -item[1]))
    elif normalized_sort == "newest":
        matched_entries.sort(
            key=lambda item: (-_offer_timestamp(item[0].offer.created_at), -item[1])
        )
    else:  # "relevance"
        matched_entries.sort(
            key=lambda item: (
                -item[1],
                -(item[0].match_score or 0),
                -_offer_timestamp(item[0].offer.created_at),
            )
        )

    total = len(matched_entries)
    sliced = matched_entries[max(0, offset) : max(0, offset) + max(1, limit)]

    output: list[ScoredOffer] = []
    for scored_offer, _, _, _, dist_km in sliced:
        if normalized_sort == "nearest" and math.isfinite(dist_km):
            dist_label = f"{dist_km:.1f} km away" if dist_km < 10 else f"{round(dist_km)} km away"
            reasons = list(scored_offer.match_reasons)
            if not any("km away" in r.lower() for r in reasons):
                reasons = [dist_label, *reasons][:3]
            output.append(replace(scored_offer, match_reasons=reasons))
        else:
            output.append(scored_offer)

    return total, output

from collections import Counter
from dataclasses import dataclass
import json
import math
import re
from app.models import CvModel, JobOfferModel

_TOKEN_RE = re.compile(r"[a-z0-9+#.]+")
_YEARS_RE = re.compile(r"(\d{1,2})\s*\+?\s*(?:years?|yrs?|lat|lata)\b")

_PHRASE_ALIASES: list[tuple[str, str]] = [
    ("amazon web services", "aws"),
    ("google cloud platform", "gcp"),
    ("google cloud", "gcp"),
    ("quality assurance", "qa"),
    ("ci/cd", "cicd"),
    ("react.js", "react"),
    ("node.js", "nodejs"),
    ("spring boot", "spring"),
    ("elastic stack", "elasticsearch"),
]

_TOKEN_ALIASES: dict[str, str] = {
    "k8s": "kubernetes",
    "py": "python",
    "postgres": "postgresql",
    "psql": "postgresql",
    "js": "javascript",
    "ts": "typescript",
    "reactjs": "react",
    "nodejs": "nodejs",
    "node": "nodejs",
    "elk": "elasticsearch",
    "qa": "testing",
    "testing": "testing",
    "cicd": "cicd",
    "async": "asyncio",
}

_STOPWORDS: set[str] = {
    "the",
    "and",
    "for",
    "with",
    "you",
    "your",
    "our",
    "role",
    "team",
    "work",
    "experience",
    "skills",
    "skill",
    "required",
    "nice",
    "have",
    "offer",
    "url",
    "https",
    "http",
    "com",
    "justjoin",
    "job",
    "location",
    "workplace",
    "full",
    "time",
    "years",
    "year",
    "using",
    "build",
    "across",
    "in",
    "on",
    "to",
    "of",
    "a",
    "an",
    "is",
    "are",
    "oraz",
    "dla",
    "praca",
    "wymagania",
    "doswiadczenie",
    "umiejetnosci",
    "znajomosc",
    "w",
    "na",
    "z",
    "do",
    "i",
}

_LEVEL_ORDINAL: dict[str, int] = {
    "INTERN": 0,
    "JUNIOR": 0,
    "MID": 1,
    "REGULAR": 1,
    "SENIOR": 2,
    "LEAD": 2,
    "STAFF": 2,
    "PRINCIPAL": 2,
    "MANAGER": 3,
    "C-LEVEL": 4,
    "C_LEVEL": 4,
}


@dataclass
class ScoredOffer:
    """Job offer bundled with a 0-100 match score (for personalized ranking) and human-readable reasons."""

    offer: JobOfferModel
    match_score: int | None
    match_reasons: list[str]


def haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    earth_radius_km = 6371.0
    d_lat = math.radians(lat2 - lat1)
    d_lon = math.radians(lon2 - lon1)
    a = (
        math.sin(d_lat / 2) ** 2
        + math.cos(math.radians(lat1))
        * math.cos(math.radians(lat2))
        * math.sin(d_lon / 2) ** 2
    )
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return earth_radius_km * c


def cosine_similarity(vec_a: list[float], vec_b: list[float]) -> float:
    if not vec_a or not vec_b or len(vec_a) != len(vec_b):
        return 0.0
    dot = sum(a * b for a, b in zip(vec_a, vec_b))
    norm_a = math.sqrt(sum(a * a for a in vec_a))
    norm_b = math.sqrt(sum(b * b for b in vec_b))
    if norm_a == 0.0 or norm_b == 0.0:
        return 0.0
    return dot / (norm_a * norm_b)


def parse_json_list(raw: str | None) -> list:
    if not raw:
        return []
    try:
        parsed = json.loads(raw)
        return parsed if isinstance(parsed, list) else []
    except json.JSONDecodeError:
        return []


def _parse_json_dict(raw: str | None) -> dict:
    if not raw:
        return {}
    try:
        parsed = json.loads(raw)
        return parsed if isinstance(parsed, dict) else {}
    except json.JSONDecodeError:
        return {}


def _canonicalize_text(text: str) -> str:
    lowered = text.lower()
    for src, dst in _PHRASE_ALIASES:
        lowered = lowered.replace(src, f" {dst} ")
    return lowered


def tokenize_list(text: str) -> list[str]:
    """Normalize text with tech aliases and return filtered canonical tokens (preserving counts)."""
    canonical = _canonicalize_text(text)
    tokens: list[str] = []
    for raw_tok in _TOKEN_RE.findall(canonical):
        tok = raw_tok.strip(".")
        if not tok:
            continue
        tok = _TOKEN_ALIASES.get(tok, tok)
        if tok in _STOPWORDS:
            continue
        tokens.append(tok)
    return tokens


def tokenize(text: str) -> set[str]:
    return set(tokenize_list(text))


def _canonical_skill_variants(skill: str) -> set[str]:
    canonical = _canonicalize_text(skill)
    tokens = tokenize(canonical)
    # Collapse e.g. "quality assurance" -> "qa" -> "testing" already in tokens
    return tokens


def matches_query(offer: JobOfferModel, query: str) -> bool:
    q = query.strip().lower()
    if not q:
        return True
    skills = [str(s).lower() for s in parse_json_list(offer.skills_json)]
    haystack = " ".join(
        [
            offer.title.lower(),
            offer.company_name.lower(),
            offer.level.lower(),
            offer.contract_type.lower(),
            offer.work_schedule.lower(),
            offer.description.lower(),
            *skills,
        ]
    )
    if q in haystack:
        return True
    return all(part in haystack for part in q.split())


def _is_remote_offer(offer: JobOfferModel) -> bool:
    combined = " ".join(
        [
            offer.contract_type or "",
            offer.work_schedule or "",
            offer.description or "",
            offer.title or "",
        ]
    ).lower()
    return any(marker in combined for marker in ("remote", "zdalna", "zdalnie"))


def _compute_tfidf_similarities(
    cv_tokens_weighted: list[str],
    offer_token_docs: list[list[str]],
) -> list[float]:
    if not cv_tokens_weighted or not offer_token_docs:
        return [0.0 for _ in offer_token_docs]

    docs = [cv_tokens_weighted, *offer_token_docs]
    num_docs = len(docs)
    df: Counter[str] = Counter()
    for doc in docs:
        df.update(set(doc))

    def _to_tfidf_vec(doc: list[str]) -> dict[str, float]:
        tf = Counter(doc)
        vec: dict[str, float] = {}
        for term, count in tf.items():
            idf = math.log((1.0 + num_docs) / (1.0 + df[term])) + 1.0
            vec[term] = (1.0 + math.log(count)) * idf
        return vec

    cv_vec = _to_tfidf_vec(cv_tokens_weighted)
    cv_norm = math.sqrt(sum(v * v for v in cv_vec.values()))
    if cv_norm == 0.0:
        return [0.0 for _ in offer_token_docs]

    similarities: list[float] = []
    for doc in offer_token_docs:
        if not doc:
            similarities.append(0.0)
            continue
        off_vec = _to_tfidf_vec(doc)
        off_norm = math.sqrt(sum(v * v for v in off_vec.values()))
        if off_norm == 0.0:
            similarities.append(0.0)
            continue
        dot = sum(cv_vec.get(term, 0.0) * weight for term, weight in off_vec.items())
        similarities.append(max(0.0, min(1.0, dot / (cv_norm * off_norm))))
    return similarities


def infer_cv_seniority(cv_text: str, headline: str = "") -> int:
    """Infer candidate seniority ordinal (0=Junior, 1=Mid, 2=Senior, 3=Manager, 4=C-Level)."""
    for source in (headline.lower(), cv_text.lower()):
        if not source.strip():
            continue
        if any(k in source for k in ("c-level", "chief technology", "cto", "vp engineering", "director of")):
            return 4
        if any(k in source for k in ("engineering manager", "team lead", "head of", "kierownik", "manager")):
            return 3
        if any(k in source for k in ("senior", "staff engineer", "principal", "tech lead", "architect")):
            return 2
        if any(k in source for k in ("junior", "intern", "trainee", "graduate", "praktykant")):
            return 0
        if "mid" in source or "regular" in source:
            return 1

    years = [int(m.group(1)) for m in _YEARS_RE.finditer(cv_text.lower())]
    if years:
        max_years = max(years)
        if max_years >= 6:
            return 2
        if max_years >= 2:
            return 1
        return 0
    return 1


def _parse_offer_seniority(level: str | None) -> int:
    normalized = (level or "MID").strip().upper()
    return _LEVEL_ORDINAL.get(normalized, 1)


def _offer_timestamp(created_at) -> float:
    return created_at.timestamp() if created_at is not None else 0.0


def score_around_you(
    offers: list[JobOfferModel],
    user_lat: float,
    user_lon: float,
    radius_km: float | None = None,
    query: str = "",
) -> list[ScoredOffer]:
    filtered = [o for o in offers if matches_query(o, query)]
    ranked: list[tuple[float, float, ScoredOffer]] = []
    for offer in filtered:
        if offer.latitude is not None and offer.longitude is not None:
            dist = haversine_km(user_lat, user_lon, offer.latitude, offer.longitude)
            if radius_km is not None and dist > radius_km:
                continue
            reasons = [f"{dist:.1f} km away" if dist < 10 else f"{round(dist)} km away"]
        else:
            dist = math.inf
            if radius_km is not None:
                continue
            reasons = []
        if _is_remote_offer(offer):
            reasons.append("Remote-friendly")
        ranked.append(
            (
                dist,
                -_offer_timestamp(offer.created_at),
                ScoredOffer(offer=offer, match_score=None, match_reasons=reasons),
            )
        )
    ranked.sort(key=lambda item: (item[0], item[1]))
    return [item[2] for item in ranked]


def rank_around_you(
    offers: list[JobOfferModel],
    user_lat: float,
    user_lon: float,
    radius_km: float | None = None,
    query: str = "",
) -> list[JobOfferModel]:
    return [s.offer for s in score_around_you(offers, user_lat, user_lon, radius_km=radius_km, query=query)]


def score_best_for_you(
    offers: list[JobOfferModel],
    cv: CvModel | None,
    query: str = "",
    user_lat: float | None = None,
    user_lon: float | None = None,
    applied_offers: list[JobOfferModel] | None = None,
) -> list[ScoredOffer]:
    """Score and rank offers using skills, TF-IDF/embeddings, title, seniority, geo/remote, and history."""
    filtered = [o for o in offers if matches_query(o, query)]
    if not filtered:
        return []

    if cv is None:
        return [
            ScoredOffer(offer=o, match_score=50, match_reasons=["General listing"])
            for o in sorted(filtered, key=lambda o: -_offer_timestamp(o.created_at))
        ]

    structured = _parse_json_dict(cv.structured_json)
    cv_headline = str(structured.get("headline") or "")
    cv_skills_raw = [str(s) for s in (structured.get("skills") or []) if str(s).strip()]
    cv_exp_raw = " ".join(str(e) for e in (structured.get("experience") or []))
    cv_summary = str(structured.get("summary") or "")
    cv_full_text = "\n".join([cv.raw_text or "", cv_headline, cv_summary, " ".join(cv_skills_raw), cv_exp_raw])

    # Build weighted CV token document (skills & headline emphasized)
    cv_tokens_weighted = (
        tokenize_list(" ".join(cv_skills_raw)) * 3
        + tokenize_list(cv_headline) * 3
        + tokenize_list(cv_full_text)
    )
    cv_all_tokens = set(cv_tokens_weighted)
    cv_role_tokens = tokenize(cv_headline) or tokenize(cv_summary[:240]) or cv_all_tokens
    cv_skill_tokens = tokenize(" ".join(cv_skills_raw)) | cv_all_tokens
    cv_seniority = infer_cv_seniority(cv_full_text, cv_headline)
    cv_embedding = parse_json_list(cv.embedding_json)

    applied_list = list(applied_offers or [])
    applied_ids = {o.id for o in applied_list}
    applied_profile_tokens: set[str] = set()
    for app_offer in applied_list:
        app_skills = " ".join(str(s) for s in parse_json_list(app_offer.skills_json))
        applied_profile_tokens |= tokenize(f"{app_offer.title} {app_skills}")

    # Build offer docs for corpus TF-IDF
    offer_parsed: list[tuple[JobOfferModel, list[str], list[str]]] = []
    for offer in filtered:
        skills = [str(s) for s in parse_json_list(offer.skills_json)]
        title_and_skills = tokenize_list(offer.title) * 2 + tokenize_list(" ".join(skills)) * 2
        body_tokens = tokenize_list(offer.description or "")
        offer_parsed.append((offer, skills, title_and_skills + body_tokens))

    tfidf_scores = _compute_tfidf_similarities(
        cv_tokens_weighted,
        [doc for _, _, doc in offer_parsed],
    )

    scored_entries: list[tuple[float, float, ScoredOffer]] = []
    for idx, (offer, offer_skills, _) in enumerate(offer_parsed):
        # 1. Skill overlap (0.38)
        matched_skills: list[str] = []
        cv_lower_blob = _canonicalize_text(cv_full_text)
        for skill in offer_skills:
            variants = _canonical_skill_variants(skill)
            skill_phrase = _canonicalize_text(skill).strip()
            if (variants and variants <= cv_skill_tokens) or (
                len(skill_phrase) >= 2 and skill_phrase in cv_lower_blob
            ):
                matched_skills.append(skill)

        semantic_sim = tfidf_scores[idx]
        offer_embedding = parse_json_list(offer.embedding_json)
        if cv_embedding and offer_embedding:
            dense_sim = max(0.0, cosine_similarity(cv_embedding, offer_embedding))
            semantic_sim = 0.5 * dense_sim + 0.5 * tfidf_scores[idx]

        skill_score = (
            len(matched_skills) / len(offer_skills)
            if offer_skills
            else semantic_sim
        )

        # 2. Title & role alignment (0.14)
        title_tokens = tokenize(offer.title)
        title_score = (
            len(title_tokens & cv_role_tokens) / max(len(title_tokens), 1)
            if title_tokens
            else 0.0
        )

        # 3. Seniority alignment (0.10)
        offer_seniority = _parse_offer_seniority(offer.level)
        seniority_diff = abs(cv_seniority - offer_seniority)
        seniority_score = {0: 1.0, 1: 0.65, 2: 0.30}.get(seniority_diff, 0.10)

        # 4. Geo proximity / remote compatibility (0.09)
        is_remote = _is_remote_offer(offer)
        dist_km: float | None = None
        if (
            user_lat is not None
            and user_lon is not None
            and offer.latitude is not None
            and offer.longitude is not None
        ):
            dist_km = haversine_km(user_lat, user_lon, offer.latitude, offer.longitude)
        if is_remote or (dist_km is not None and dist_km <= 25.0):
            geo_score = 1.0
        elif dist_km is not None:
            geo_score = math.exp(-dist_km / 180.0)
        else:
            geo_score = 0.5

        # 5. Application history affinity (0.05)
        if applied_profile_tokens:
            offer_profile_tokens = tokenize(f"{offer.title} {' '.join(offer_skills)}")
            union_len = max(len(applied_profile_tokens | offer_profile_tokens), 1)
            history_score = len(applied_profile_tokens & offer_profile_tokens) / union_len
        else:
            history_score = 0.5

        raw_score = (
            0.38 * skill_score
            + 0.24 * semantic_sim
            + 0.14 * title_score
            + 0.10 * seniority_score
            + 0.09 * geo_score
            + 0.05 * history_score
        )
        if offer.id in applied_ids:
            raw_score -= 0.08
        raw_score = max(0.0, min(1.0, raw_score))
        match_score = int(round(raw_score * 100))

        reasons: list[str] = []
        if matched_skills:
            reasons.append(f"Skills: {', '.join(matched_skills[:3])}")
        if title_score >= 0.35 or seniority_diff == 0:
            reasons.append(f"{offer.level.title()} role alignment")
        if is_remote:
            reasons.append("Remote-friendly")
        elif dist_km is not None and dist_km <= 35.0:
            reasons.append(f"{dist_km:.1f} km away" if dist_km < 10 else f"{round(dist_km)} km away")
        if not reasons:
            reasons.append("Matches profile keywords" if semantic_sim >= 0.1 else "Recently listed")

        scored_entries.append(
            (
                raw_score,
                _offer_timestamp(offer.created_at),
                ScoredOffer(offer=offer, match_score=match_score, match_reasons=reasons[:3]),
            )
        )

    scored_entries.sort(key=lambda item: (-item[0], -item[1]))
    return [item[2] for item in scored_entries]


def rank_best_for_you(
    offers: list[JobOfferModel],
    cv: CvModel | None,
    query: str = "",
) -> list[JobOfferModel]:
    return [s.offer for s in score_best_for_you(offers=offers, cv=cv, query=query)]

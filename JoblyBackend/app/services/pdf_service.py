from io import BytesIO
import os
from pathlib import Path
import re
from fpdf import FPDF
from pypdf import PdfReader
from app.schemas import CvStructuredData

_UNICODE_REPLACEMENTS = {
    "\u2014": "-",
    "\u2013": "-",
    "\u2022": "*",
    "\u2018": "'",
    "\u2019": "'",
    "\u201c": '"',
    "\u201d": '"',
    "\u2026": "...",
    "\u00a0": " ",
}

_TTF_FONT_CANDIDATES: list[tuple[Path, Path]] = [
    (Path("C:/Windows/Fonts/arial.ttf"), Path("C:/Windows/Fonts/arialbd.ttf")),
    (Path("C:/Windows/Fonts/segoeui.ttf"), Path("C:/Windows/Fonts/segoeuib.ttf")),
    (
        Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
        Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"),
    ),
]

_EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
_PHONE_RE = re.compile(r"(?:\+?\d[\d\s\-()]{6,}\d)")

_SECTION_ALIASES: dict[str, str] = {
    "summary": "summary",
    "profile": "summary",
    "professional summary": "summary",
    "about": "summary",
    "about me": "summary",
    "objective": "summary",
    "o mnie": "summary",
    "podsumowanie": "summary",
    "profil": "summary",
    "profil zawodowy": "summary",
    "skills": "skills",
    "technical skills": "skills",
    "core skills": "skills",
    "key skills": "skills",
    "technologies": "skills",
    "tech stack": "skills",
    "umiejetnosci": "skills",
    "umiejętności": "skills",
    "technologie": "skills",
    "kompetencje": "skills",
    "experience": "experience",
    "work experience": "experience",
    "professional experience": "experience",
    "employment": "experience",
    "employment history": "experience",
    "career history": "experience",
    "doswiadczenie": "experience",
    "doświadczenie": "experience",
    "doswiadczenie zawodowe": "experience",
    "doświadczenie zawodowe": "experience",
    "education": "education",
    "academic background": "education",
    "wyksztalcenie": "education",
    "wykształcenie": "education",
    "edukacja": "education",
    "languages": "languages",
    "language skills": "languages",
    "jezyki": "languages",
    "języki": "languages",
    "jezyki obce": "languages",
    "języki obce": "languages",
    "certifications": "certifications",
    "certificates": "certifications",
    "licenses": "certifications",
    "certyfikaty": "certifications",
    "kursy": "certifications",
    "projects": "extra:Projects",
    "key projects": "extra:Projects",
    "projekty": "extra:Projects",
    "interests": "extra:Interests",
    "zainteresowania": "extra:Interests",
    "achievements": "extra:Achievements",
    "osiagniecia": "extra:Achievements",
    "osiągnięcia": "extra:Achievements",
}

_KNOWN_TECH_KEYWORDS: list[str] = [
    "Python",
    "FastAPI",
    "Django",
    "Flask",
    "Rust",
    "Kotlin",
    "Java",
    "Go",
    "C++",
    "TypeScript",
    "JavaScript",
    "React",
    "Angular",
    "Node.js",
    "PostgreSQL",
    "pgvector",
    "MySQL",
    "Redis",
    "MongoDB",
    "SQL",
    "Docker",
    "Kubernetes",
    "Linux",
    "AWS",
    "GCP",
    "Azure",
    "Terraform",
    "CI/CD",
    "Git",
    "Pytest",
    "Playwright",
    "Selenium",
    "ELK",
    "Kafka",
    "GraphQL",
    "REST",
    "AsyncIO",
]


class PdfValidationError(Exception):
    """Raised when an uploaded PDF fails size, structure, encryption, or page-count checks."""

    def __init__(self, status_code: int, detail: str) -> None:
        super().__init__(detail)
        self.status_code = status_code
        self.detail = detail


def sanitize_pdf_text(text: str) -> str:
    cleaned = text
    for src, dst in _UNICODE_REPLACEMENTS.items():
        cleaned = cleaned.replace(src, dst)
    return cleaned.encode("latin-1", errors="replace").decode("latin-1")


def sanitize_filename(file_name: str, max_length: int = 100) -> str:
    raw = (file_name or "").replace("\\", "/").strip()
    base = Path(raw or "new-cv.pdf").name
    safe = re.sub(r"[^A-Za-z0-9._-]+", "-", base)
    safe = re.sub(r"\.{2,}", ".", safe).strip(".-")
    if not safe:
        safe = "new-cv"
    if safe.lower().endswith(".pdf"):
        stem = safe[:-4].strip(".-") or "new-cv"
    else:
        stem = safe
    max_stem = max(1, max_length - 4)
    return f"{stem[:max_stem]}.pdf"


def validate_and_extract_pdf(
    content: bytes,
    max_bytes: int,
    max_pages: int,
) -> tuple[int, str]:
    """Validate uploaded PDF bytes against limits and extract up to 30,000 characters of text."""
    if not content:
        raise PdfValidationError(400, "Uploaded PDF file is empty.")

    if len(content) > max_bytes:
        limit_mb = f"{max_bytes / (1024 * 1024):.1f}".rstrip("0").rstrip(".")
        raise PdfValidationError(413, f"PDF exceeds maximum size limit of {limit_mb} MB.")

    if not content.startswith(b"%PDF-"):
        raise PdfValidationError(400, "Uploaded file is not a valid PDF document.")

    try:
        reader = PdfReader(BytesIO(content))
    except Exception as exc:
        raise PdfValidationError(400, f"Could not parse PDF file: {exc}") from exc

    if reader.is_encrypted:
        raise PdfValidationError(400, "Encrypted or password-protected PDFs are not supported.")

    page_count = len(reader.pages)
    if page_count == 0:
        raise PdfValidationError(400, "Uploaded PDF has zero pages.")

    if page_count > max_pages:
        raise PdfValidationError(
            400,
            f"PDF exceeds maximum limit of {max_pages} pages (got {page_count}).",
        )

    chunks: list[str] = []
    for page in reader.pages:
        try:
            text = (page.extract_text() or "").strip()
        except Exception:
            text = ""
        if text:
            chunks.append(text)

    extracted = "\n".join(chunks)[:30_000]
    return page_count, extracted


def _match_heading(line: str) -> tuple[str, str | None] | None:
    """Return `(section_key, inline_value)` if `line` starts with a known CV heading."""
    cleaned = re.sub(r"^[\s*#\-\u2022]+", "", line).strip()
    if not cleaned:
        return None

    # Handle "Heading: value" on a single line (e.g., "Skills: Python, FastAPI")
    if ":" in cleaned:
        head_part, rest = cleaned.split(":", 1)
        normalized_head = re.sub(r"\s+", " ", head_part).strip(" -–—").lower()
        if normalized_head in _SECTION_ALIASES:
            return _SECTION_ALIASES[normalized_head], rest.strip() or None

    normalized = re.sub(r"\s+", " ", cleaned.rstrip(":")).strip(" -–—").lower()
    if normalized in _SECTION_ALIASES:
        return _SECTION_ALIASES[normalized], None

    return None


def _split_list_tokens(raw_lines: list[str]) -> list[str]:
    items: list[str] = []
    seen: set[str] = set()
    for line in raw_lines:
        cleaned = line.replace("\u2022", ",").replace("|", ",").replace(";", ",")
        cleaned = re.sub(r"\s+/\s+", ",", cleaned)
        for token in cleaned.split(","):
            candidate = token.strip().lstrip("-*•· ").strip()
            if not candidate:
                continue
            key = candidate.lower()
            if key not in seen:
                seen.add(key)
                items.append(candidate)
    return items


def _scan_known_skills(raw_text: str) -> list[str]:
    lower_text = raw_text.lower()
    found: list[str] = []
    for kw in _KNOWN_TECH_KEYWORDS:
        pattern = r"(?<![a-z0-9])" + re.escape(kw.lower()) + r"(?![a-z0-9])"
        if re.search(pattern, lower_text):
            found.append(kw)
    return found


def parse_cv_text_to_structured(
    raw_text: str,
    fallback_name: str,
    fallback_phone: str,
    fallback_email: str,
) -> CvStructuredData:
    """Parse extracted CV text into structured fields without injecting fake defaults."""
    lines = [line.strip() for line in raw_text.splitlines() if line.strip()]

    email_match = _EMAIL_RE.search(raw_text)
    phone_match = _PHONE_RE.search(raw_text)
    email = email_match.group(0) if email_match else fallback_email
    phone = phone_match.group(0).strip() if phone_match else fallback_phone

    leading_lines: list[str] = []
    sections: dict[str, list[str]] = {}
    extra_sections: dict[str, list[str]] = {}
    current_section: str | None = None

    for line in lines:
        heading_match = _match_heading(line)
        if heading_match is not None:
            current_section, inline_val = heading_match
            target_list = (
                extra_sections.setdefault(current_section.split(":", 1)[1], [])
                if current_section.startswith("extra:")
                else sections.setdefault(current_section, [])
            )
            if inline_val:
                target_list.append(inline_val)
            continue

        if current_section is None:
            leading_lines.append(line)
        elif current_section.startswith("extra:"):
            extra_sections.setdefault(current_section.split(":", 1)[1], []).append(line)
        else:
            sections.setdefault(current_section, []).append(line)

    # Extract header name & headline from non-contact leading lines
    header_candidates: list[str] = []
    for line in leading_lines:
        stripped = _EMAIL_RE.sub("", line)
        stripped = _PHONE_RE.sub("", stripped).strip(" |,-•/")
        if stripped:
            header_candidates.append(stripped)

    full_name = fallback_name
    headline = ""
    if header_candidates:
        if len(header_candidates) >= 2:
            full_name = header_candidates[0][:120]
            headline = header_candidates[1][:160]
        elif len(header_candidates[0].split()) <= 4 and sections:
            full_name = header_candidates[0][:120]
        elif not sections:
            # Unstructured single-line/compact PDF: preserve header text cleanly in headline or summary
            headline = header_candidates[0][:160]

    summary_lines = sections.get("summary", [])
    summary = "\n".join(summary_lines).strip()

    skills = _split_list_tokens(sections.get("skills", []))
    experience = [
        line.lstrip("-*• ").strip()
        for line in sections.get("experience", [])
        if line.lstrip("-*• ").strip()
    ]
    education = "\n".join(sections.get("education", [])).strip()
    languages = _split_list_tokens(sections.get("languages", []))
    certifications = [
        line.lstrip("-*• ").strip()
        for line in sections.get("certifications", [])
        if line.lstrip("-*• ").strip()
    ]

    if not summary and not experience and not education and not skills and raw_text.strip():
        summary = raw_text.strip()[:4000]

    if not skills and raw_text.strip():
        skills = _scan_known_skills(raw_text)

    return CvStructuredData(
        full_name=full_name,
        phone=phone,
        email=email,
        headline=headline,
        summary=summary,
        skills=skills,
        experience=experience,
        education=education,
        languages=languages,
        certifications=certifications,
        extra_sections={k: v for k, v in extra_sections.items() if v},
    )


def _find_unicode_font_pair() -> tuple[Path, Path] | None:
    for regular, bold in _TTF_FONT_CANDIDATES:
        if regular.exists() and bold.exists():
            return regular, bold
    return None


def _render_text(text: str, use_unicode: bool) -> str:
    if use_unicode:
        return "".join(ch for ch in text if ch == "\n" or ord(ch) >= 32)
    return sanitize_pdf_text(text)


def render_cv_pdf(data: CvStructuredData, destination: Path) -> Path:
    """Render structured CV data into a PDF file atomically, using Unicode TTF fonts when available."""
    destination.parent.mkdir(parents=True, exist_ok=True)
    pdf = FPDF()
    pdf.set_auto_page_break(auto=True, margin=15)
    pdf.add_page()

    font_pair = _find_unicode_font_pair()
    use_unicode = False
    font_family = "Helvetica"
    if font_pair is not None:
        try:
            pdf.add_font("JoblySans", "", str(font_pair[0]))
            pdf.add_font("JoblySans", "B", str(font_pair[1]))
            font_family = "JoblySans"
            use_unicode = True
        except Exception:
            font_family = "Helvetica"
            use_unicode = False

    # Header
    pdf.set_font(font_family, "B", 18)
    pdf.cell(0, 10, _render_text(data.full_name or "Candidate", use_unicode), new_x="LMARGIN", new_y="NEXT")

    if data.headline.strip():
        pdf.set_font(font_family, "", 11)
        pdf.cell(0, 6, _render_text(data.headline, use_unicode), new_x="LMARGIN", new_y="NEXT")

    contact_parts = [p.strip() for p in [data.phone, data.email] if p and p.strip()]
    if contact_parts:
        pdf.set_font(font_family, "", 10)
        pdf.set_text_color(90, 90, 90)
        pdf.cell(0, 6, _render_text("  |  ".join(contact_parts), use_unicode), new_x="LMARGIN", new_y="NEXT")
        pdf.set_text_color(0, 0, 0)

    pdf.ln(4)
    pdf.set_draw_color(255, 204, 0)
    pdf.set_line_width(0.8)
    pdf.line(pdf.l_margin, pdf.get_y(), pdf.w - pdf.r_margin, pdf.get_y())
    pdf.ln(5)

    # Summary
    if data.summary.strip():
        _section_heading(pdf, "Summary", font_family, use_unicode)
        pdf.set_font(font_family, "", 10)
        pdf.set_x(pdf.l_margin)
        pdf.multi_cell(0, 5.5, _render_text(data.summary, use_unicode))
        pdf.ln(4)

    # Skills
    if data.skills:
        _section_heading(pdf, "Skills", font_family, use_unicode)
        pdf.set_font(font_family, "", 10)
        pdf.set_x(pdf.l_margin)
        pdf.multi_cell(0, 5.5, _render_text(", ".join(data.skills), use_unicode))
        pdf.ln(4)

    # Experience
    if data.experience:
        _section_heading(pdf, "Experience", font_family, use_unicode)
        pdf.set_font(font_family, "", 10)
        for item in data.experience:
            pdf.set_x(pdf.l_margin)
            pdf.multi_cell(0, 5.5, _render_text(f"- {item}", use_unicode))
            pdf.ln(1.5)
        pdf.ln(3)

    # Education
    if data.education.strip():
        _section_heading(pdf, "Education", font_family, use_unicode)
        pdf.set_font(font_family, "", 10)
        pdf.set_x(pdf.l_margin)
        pdf.multi_cell(0, 5.5, _render_text(data.education, use_unicode))
        pdf.ln(3)

    # Languages
    if data.languages:
        _section_heading(pdf, "Languages", font_family, use_unicode)
        pdf.set_font(font_family, "", 10)
        pdf.set_x(pdf.l_margin)
        pdf.multi_cell(0, 5.5, _render_text(", ".join(data.languages), use_unicode))
        pdf.ln(3)

    # Certifications
    if data.certifications:
        _section_heading(pdf, "Certifications", font_family, use_unicode)
        pdf.set_font(font_family, "", 10)
        for cert in data.certifications:
            pdf.set_x(pdf.l_margin)
            pdf.multi_cell(0, 5.5, _render_text(f"- {cert}", use_unicode))
            pdf.ln(1.0)
        pdf.ln(2)

    # Extra sections parsed from uploaded PDFs
    for section_title, entries in data.extra_sections.items():
        if not entries:
            continue
        _section_heading(pdf, section_title, font_family, use_unicode)
        pdf.set_font(font_family, "", 10)
        for entry in entries:
            pdf.set_x(pdf.l_margin)
            pdf.multi_cell(0, 5.5, _render_text(f"- {entry}", use_unicode))
            pdf.ln(1.0)
        pdf.ln(2)

    pdf_bytes = bytes(pdf.output())
    tmp_path = destination.with_name(f".{destination.name}.{os.getpid()}.tmp")
    tmp_path.write_bytes(pdf_bytes)
    tmp_path.replace(destination)
    return destination


def _section_heading(pdf: FPDF, title: str, font_family: str, use_unicode: bool) -> None:
    pdf.set_font(font_family, "B", 12)
    pdf.set_text_color(30, 30, 30)
    pdf.cell(0, 7, _render_text(title, use_unicode), new_x="LMARGIN", new_y="NEXT")
    pdf.set_text_color(0, 0, 0)


def extract_text_from_pdf(file_path: Path) -> str:
    reader = PdfReader(str(file_path))
    chunks: list[str] = []
    for page in reader.pages:
        text = page.extract_text() or ""
        if text.strip():
            chunks.append(text.strip())
    return "\n".join(chunks)


def inspect_pdf_file(file_path: Path) -> tuple[int | None, int | None]:
    """Return `(page_count, file_size_bytes)` for an existing PDF on disk."""
    try:
        if not file_path.exists():
            return None, None
        size_bytes = file_path.stat().st_size
        reader = PdfReader(str(file_path))
        return len(reader.pages), size_bytes
    except Exception:
        return None, None


def build_plain_text(data: CvStructuredData) -> str:
    lines: list[str] = [data.full_name]
    if data.headline.strip():
        lines.append(data.headline)
    contact = " ".join(p for p in [data.phone, data.email] if p).strip()
    if contact:
        lines.append(contact)
    if data.summary.strip():
        lines.append(f"Summary: {data.summary}")
    if data.skills:
        lines.append(f"Skills: {', '.join(data.skills)}")
    if data.experience:
        lines.append("Experience:")
        lines.extend(f"- {exp}" for exp in data.experience)
    if data.education.strip():
        lines.append(f"Education: {data.education}")
    if data.languages:
        lines.append(f"Languages: {', '.join(data.languages)}")
    if data.certifications:
        lines.append("Certifications:")
        lines.extend(f"- {cert}" for cert in data.certifications)
    for title, entries in data.extra_sections.items():
        if entries:
            lines.append(f"{title}:")
            lines.extend(f"- {entry}" for entry in entries)
    return "\n".join(lines)


_CITY_COORDINATES: list[tuple[str, float, float, tuple[str, ...]]] = [
    ("Krakow", 50.0647, 19.9450, ("krakow", "kraków", "krakowie", "cracow")),
    ("Warszawa", 52.2297, 21.0122, ("warszawa", "warsaw", "warszawie", "warszawy")),
    ("Wroclaw", 51.1079, 17.0385, ("wroclaw", "wrocław", "wrocławiu", "wroclawiu")),
    ("Gdansk", 54.3520, 18.6466, ("gdansk", "gdańsk", "gdańsku", "gdansku")),
    ("Poznan", 52.4064, 16.9252, ("poznan", "poznań", "poznaniu")),
    ("Lodz", 51.7592, 19.4560, ("lodz", "łódź", "łodzi")),
    ("Katowice", 50.2649, 19.0238, ("katowice", "katowicach")),
    ("Gdynia", 54.5189, 18.5305, ("gdynia", "gdyni")),
    ("Sopot", 54.4416, 18.5601, ("sopot", "sopocie")),
    ("Szczecin", 53.4285, 14.5528, ("szczecin", "szczecinie")),
    ("Lublin", 51.2465, 22.5684, ("lublin", "lublinie")),
    ("Bialystok", 53.1325, 23.1688, ("bialystok", "białystok", "białymstoku")),
    ("Bydgoszcz", 53.1235, 18.0084, ("bydgoszcz", "bydgoszczy")),
    ("Rzeszow", 50.0412, 21.9991, ("rzeszow", "rzeszów", "rzeszowie")),
    ("Torun", 53.0138, 18.5984, ("torun", "toruń", "toruniu")),
    ("Gliwice", 50.2945, 18.6714, ("gliwice", "gliwicach")),
    ("Kielce", 50.8661, 20.6286, ("kielce", "kielcach")),
    ("Olsztyn", 53.7784, 20.4801, ("olsztyn", "olsztynie")),
    ("Opole", 50.6751, 17.9213, ("opole", "opolu")),
    ("Bielsko-Biala", 49.8224, 19.0444, ("bielsko-biala", "bielsko-biała")),
    ("Berlin", 52.5200, 13.4050, ("berlin",)),
    ("London", 51.5072, -0.1276, ("london", "londyn")),
    ("Munich", 48.1351, 11.5820, ("munich", "münchen", "monachium")),
    ("Amsterdam", 52.3676, 4.9041, ("amsterdam",)),
    ("Prague", 50.0755, 14.4378, ("prague", "praga", "praha")),
    ("Vienna", 48.2082, 16.3738, ("vienna", "wien", "wiedeń")),
    ("Zurich", 47.3769, 8.5417, ("zurich", "zürich")),
    ("Dublin", 53.3498, -6.2603, ("dublin",)),
]

_LOCATION_HINT_RE = re.compile(
    r"\b(?:location|address|city|based\s+in|lokalizacja|miasto|adres|zamieszkania)\b",
    re.IGNORECASE,
)


def _find_city_in_text(text: str) -> tuple[str, float, float] | None:
    lowered = text.lower()
    best_pos: int | None = None
    best_match: tuple[str, float, float] | None = None
    for canonical, lat, lon, aliases in _CITY_COORDINATES:
        for alias in aliases:
            match = re.search(rf"(?<!\w){re.escape(alias)}(?!\w)", lowered)
            if match is not None:
                if best_pos is None or match.start() < best_pos:
                    best_pos = match.start()
                    best_match = (canonical, lat, lon)
    return best_match


def extract_location_from_cv_text(raw_text: str) -> tuple[str, float, float] | None:
    """Extract `(city, latitude, longitude)` from CV text if present, otherwise return `None`."""
    if not raw_text or not raw_text.strip():
        return None

    lines = [line.strip() for line in raw_text.splitlines() if line.strip()]

    # 1. Explicit location/address lines first
    for line in lines:
        if _LOCATION_HINT_RE.search(line):
            found = _find_city_in_text(line)
            if found is not None:
                return found

    # 2. Top header lines (first 6 non-empty lines) before scanning full body
    header_block = "\n".join(lines[:6])
    found = _find_city_in_text(header_block)
    if found is not None:
        return found

    # 3. Full CV text fallback
    return _find_city_in_text(raw_text)


async def sync_user_location_from_cv_text(
    session,
    user,
    raw_text: str,
    preferred_text: str | None = None,
) -> tuple[str, float, float] | None:
    """Update user coordinates & settings city if CV text contains a recognized location; else skip."""
    from sqlalchemy import select
    from app.models import UserSettingsModel, utc_now

    detected = (
        extract_location_from_cv_text(preferred_text)
        if preferred_text and preferred_text.strip()
        else None
    ) or extract_location_from_cv_text(raw_text)
    if detected is None:
        return None

    city, lat, lon = detected
    user.latitude = lat
    user.longitude = lon

    settings_row = (
        await session.execute(
            select(UserSettingsModel).where(UserSettingsModel.user_id == user.id)
        )
    ).scalar_one_or_none()
    if settings_row is None:
        settings_row = UserSettingsModel(user_id=user.id, city=city)
        session.add(settings_row)
    else:
        settings_row.city = city
        settings_row.updated_at = utc_now()

    return detected
